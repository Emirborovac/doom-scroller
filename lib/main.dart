import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const CoastApp());

/// Flat palette. No elevation anywhere -- depth comes from surface contrast
/// and spacing rather than shadows.
class Palette {
  const Palette({
    required this.bg,
    required this.surface,
    required this.surfaceAlt,
    required this.text,
    required this.muted,
  });

  final Color bg;
  final Color surface;
  final Color surfaceAlt;
  final Color text;
  final Color muted;

  static const accent = Color(0xFF6C5CE7);
  static const stop = Color(0xFFFF5C7A);

  static const dark = Palette(
    bg: Color(0xFF0B0B10),
    surface: Color(0xFF15151E),
    surfaceAlt: Color(0xFF1D1D28),
    text: Color(0xFFF2F2F5),
    muted: Color(0xFF8B8B9B),
  );

  static const light = Palette(
    bg: Color(0xFFF6F6F9),
    surface: Color(0xFFFFFFFF),
    surfaceAlt: Color(0xFFEFEFF4),
    text: Color(0xFF14141A),
    muted: Color(0xFF6E6E7E),
  );

  static Palette of(BuildContext c) =>
      Theme.of(c).brightness == Brightness.dark ? dark : light;
}

class CoastApp extends StatelessWidget {
  const CoastApp({super.key});

  ThemeData _theme(Brightness b) {
    final p = b == Brightness.dark ? Palette.dark : Palette.light;
    return ThemeData(
      useMaterial3: true,
      brightness: b,
      scaffoldBackgroundColor: p.bg,
      colorScheme: ColorScheme.fromSeed(
        seedColor: Palette.accent,
        brightness: b,
      ).copyWith(surface: p.surface),
      sliderTheme: SliderThemeData(
        activeTrackColor: Palette.accent,
        inactiveTrackColor: p.surfaceAlt,
        thumbColor: Palette.accent,
        overlayColor: Palette.accent.withValues(alpha: 0.12),
        trackHeight: 6,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Coast',
      debugShowCheckedModeBanner: false,
      theme: _theme(Brightness.light),
      darkTheme: _theme(Brightness.dark),
      home: const HomePage(),
    );
  }
}

/// The Coast mark: three bars narrowing downward.
class CoastMark extends StatelessWidget {
  const CoastMark({super.key, this.size = 22, this.color = Colors.white});

  final double size;
  final Color color;

  @override
  Widget build(BuildContext context) {
    // Proportions match the launcher icon exactly: 100% / 70% / 40% widths,
    // bar height and gap in a 10:6 ratio.
    final bar = size * (10 / 42);
    final gap = size * (6 / 42);
    Widget b(double f) => Container(
          width: size * 1.19 * f,
          height: bar,
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(bar / 2),
          ),
        );
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        b(1.0),
        SizedBox(height: gap),
        b(0.7),
        SizedBox(height: gap),
        b(0.4),
      ],
    );
  }
}

class AppEntry {
  const AppEntry({required this.package, required this.label, required this.icon});
  final String package;
  final String label;
  final Uint8List icon;
}

/// Mirrors the map returned by the `getStatus` platform call.
class Status {
  const Status({
    this.serviceEnabled = false,
    this.serviceBound = false,
    this.feed = 'NONE',
    this.feedLabel = '',
    this.onFeed = false,
    this.autoScroll = false,
    this.smartTiming = true,
    this.intervalSeconds = 8,
    this.secondsUntilNext = -1,
  });

  final bool serviceEnabled;
  final bool serviceBound;
  final String feed;
  final String feedLabel;
  final bool onFeed;
  final bool autoScroll;
  final bool smartTiming;
  final int intervalSeconds;
  final int secondsUntilNext;

  /// Which supported app the current feed belongs to, if any.
  String? get activePackage => switch (feed) {
        'YOUTUBE_SHORTS' => 'com.google.android.youtube',
        'INSTAGRAM_REELS' => 'com.instagram.android',
        'TIKTOK_FOR_YOU' || 'TIKTOK_FRIENDS' => 'com.zhiliaoapp.musically',
        _ => null,
      };

  factory Status.fromMap(Map<dynamic, dynamic> m) => Status(
        serviceEnabled: m['serviceEnabled'] as bool? ?? false,
        serviceBound: m['serviceBound'] as bool? ?? false,
        feed: m['feed'] as String? ?? 'NONE',
        feedLabel: m['feedLabel'] as String? ?? '',
        onFeed: m['onFeed'] as bool? ?? false,
        autoScroll: m['autoScroll'] as bool? ?? false,
        smartTiming: m['smartTiming'] as bool? ?? true,
        intervalSeconds: m['intervalSeconds'] as int? ?? 8,
        secondsUntilNext: m['secondsUntilNext'] as int? ?? -1,
      );
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> with WidgetsBindingObserver {
  static const _channel = MethodChannel('coast/control');

  Status _status = const Status();
  List<AppEntry> _apps = const [];
  Timer? _poll;

  /// The user said no to the accessibility disclosure. Not persisted: the
  /// question may be asked again next launch, but never re-asked in a loop
  /// within one, and declining never enables anything.
  bool _declined = false;

  // Held locally so dragging stays smooth regardless of poll rate.
  double _interval = 8;
  bool _dragging = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _poll = Timer.periodic(const Duration(seconds: 1), (_) => _refresh());
    _refresh();
    _loadApps();
  }

  @override
  void dispose() {
    _poll?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  // The user grants the service in system settings and comes back, so the
  // status has to update without a manual refresh.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _refresh();
  }

  Future<void> _loadApps() async {
    try {
      final raw = await _channel.invokeMethod<List<dynamic>>('getSupportedApps');
      if (raw == null || !mounted) return;
      setState(() {
        _apps = raw
            .map((e) => AppEntry(
                  package: e['package'] as String,
                  label: e['label'] as String,
                  icon: e['icon'] as Uint8List,
                ))
            .toList();
      });
    } on PlatformException {
      // Leave the row empty rather than showing placeholders.
    }
  }

  Future<void> _refresh() async {
    try {
      final m = await _channel.invokeMethod<Map<dynamic, dynamic>>('getStatus');
      if (m == null || !mounted) return;
      final s = Status.fromMap(m);
      setState(() {
        _status = s;
        if (!_dragging) _interval = s.intervalSeconds.toDouble();
      });
    } on PlatformException {
      // Service not up yet; the next tick will pick it up.
    }
  }

  Future<void> _call(String method, [Map<String, dynamic>? args]) async {
    await _channel.invokeMethod(method, args);
    _refresh();
  }

  @override
  Widget build(BuildContext context) {
    final p = Palette.of(context);
    final s = _status;

    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(24, 32, 24, 40),
          children: [
            Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: Palette.accent,
                    borderRadius: BorderRadius.circular(13),
                  ),
                  child: const Center(child: CoastMark(size: 19)),
                ),
                const SizedBox(width: 13),
                Text(
                  'Coast',
                  style: TextStyle(
                    color: p.text,
                    fontSize: 27,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.7,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 32),

            if (!s.serviceEnabled)
              _declined
                  ? _DeclinedCard(
                      palette: p,
                      onReview: () => setState(() => _declined = false),
                    )
                  : _SetupCard(
                      palette: p,
                      onAgree: () =>
                          _channel.invokeMethod('openAccessibilitySettings'),
                      onDecline: () => setState(() => _declined = true),
                    )
            else if (!s.serviceBound)
              _StartingCard(palette: p)
            else ...[
              Text(
                'Coast scrolls short-video feeds for you.',
                style: TextStyle(
                  color: p.text,
                  fontSize: 17,
                  fontWeight: FontWeight.w600,
                  height: 1.35,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Turn it on, then open one of these apps. Each video advances '
                'on its own — no tapping, no swiping.',
                style: TextStyle(color: p.muted, fontSize: 14.5, height: 1.5),
              ),
              const SizedBox(height: 22),
              if (_apps.isNotEmpty)
                _AppsRow(
                  palette: p,
                  apps: _apps,
                  activePackage: s.activePackage,
                  onTap: (pkg) => _call('openApp', {'package': pkg}),
                ),
              const SizedBox(height: 22),
              _PowerButton(
                active: s.autoScroll,
                palette: p,
                onTap: () => _call('setAutoScroll', {'enabled': !s.autoScroll}),
              ),
              if (s.autoScroll) ...[
                const SizedBox(height: 12),
                Center(
                  child: Text(
                    'Open a feed and it starts scrolling',
                    style: TextStyle(color: p.muted, fontSize: 13.5),
                  ),
                ),
              ],
              const SizedBox(height: 22),
              _SettingsCard(
                palette: p,
                interval: _interval,
                smartTiming: s.smartTiming,
                onIntervalChanged: (v) => setState(() {
                  _dragging = true;
                  _interval = v;
                }),
                onIntervalCommit: (v) {
                  _dragging = false;
                  _call('setInterval', {'seconds': v.round()});
                },
                onSmartTimingChanged: (v) =>
                    _call('setSmartTiming', {'enabled': v}),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

// ---------------------------------------------------------------- components

class _Panel extends StatelessWidget {
  const _Panel({required this.palette, required this.child, this.padding});

  final Palette palette;
  final Widget child;
  final EdgeInsets? padding;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: padding ?? const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: palette.surface,
        borderRadius: BorderRadius.circular(20),
      ),
      child: child,
    );
  }
}

class _AppsRow extends StatelessWidget {
  const _AppsRow({
    required this.palette,
    required this.apps,
    required this.activePackage,
    required this.onTap,
  });

  final Palette palette;
  final List<AppEntry> apps;
  final String? activePackage;
  final ValueChanged<String> onTap;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        for (final a in apps) ...[
          Expanded(
            child: GestureDetector(
              onTap: () => onTap(a.package),
              child: Container(
                padding: const EdgeInsets.symmetric(vertical: 14),
                decoration: BoxDecoration(
                  color: palette.surface,
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(
                    color: a.package == activePackage
                        ? Palette.accent
                        : Colors.transparent,
                    width: 1.6,
                  ),
                ),
                child: Column(
                  children: [
                    Opacity(
                      opacity: activePackage == null ||
                              a.package == activePackage
                          ? 1
                          : 0.35,
                      child: Image.memory(a.icon, width: 30, height: 30),
                    ),
                    const SizedBox(height: 7),
                    Text(
                      a.label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: a.package == activePackage
                            ? palette.text
                            : palette.muted,
                        fontSize: 11.5,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          if (a != apps.last) const SizedBox(width: 10),
        ],
      ],
    );
  }
}

class _PowerButton extends StatelessWidget {
  const _PowerButton({
    required this.active,
    required this.palette,
    required this.onTap,
  });

  final bool active;
  final Palette palette;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: active ? Palette.stop : Palette.accent,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: SizedBox(
          height: 66,
          child: Center(
            child: Text(
              active ? 'Stop' : 'Start',
              style: const TextStyle(
                color: Colors.white,
                fontSize: 17,
                fontWeight: FontWeight.w700,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _SettingsCard extends StatelessWidget {
  const _SettingsCard({
    required this.palette,
    required this.interval,
    required this.smartTiming,
    required this.onIntervalChanged,
    required this.onIntervalCommit,
    required this.onSmartTimingChanged,
  });

  final Palette palette;
  final double interval;
  final bool smartTiming;
  final ValueChanged<double> onIntervalChanged;
  final ValueChanged<double> onIntervalCommit;
  final ValueChanged<bool> onSmartTimingChanged;

  @override
  Widget build(BuildContext context) {
    return _Panel(
      palette: palette,
      padding: const EdgeInsets.fromLTRB(20, 18, 20, 10),
      child: Column(
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.baseline,
            textBaseline: TextBaseline.alphabetic,
            children: [
              Expanded(
                child: Text(
                  'Seconds per video',
                  style: TextStyle(
                    color: palette.text,
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              Text(
                '${interval.round()}',
                style: TextStyle(
                  color: palette.text,
                  fontSize: 24,
                  fontWeight: FontWeight.w700,
                  letterSpacing: -0.6,
                ),
              ),
            ],
          ),
          Slider(
            value: interval,
            min: 3,
            max: 60,
            divisions: 57,
            onChanged: onIntervalChanged,
            onChangeEnd: onIntervalCommit,
          ),
          Divider(color: palette.surfaceAlt, height: 22),
          Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Match video length',
                        style: TextStyle(
                          color: palette.text,
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 3),
                      Text(
                        'YouTube Shorts only',
                        style: TextStyle(color: palette.muted, fontSize: 13),
                      ),
                    ],
                  ),
                ),
                Switch(
                  value: smartTiming,
                  onChanged: onSmartTimingChanged,
                  activeColor: Colors.white,
                  activeTrackColor: Palette.accent,
                  inactiveTrackColor: palette.surfaceAlt,
                  trackOutlineColor:
                      WidgetStateProperty.all(Colors.transparent),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _SetupCard extends StatelessWidget {
  const _SetupCard({
    required this.palette,
    required this.onAgree,
    required this.onDecline,
  });

  final Palette palette;

  /// Consent is exactly one gesture: tapping I agree. Declining, backing out,
  /// or navigating away enables nothing -- which is precisely what Play's
  /// prominent-disclosure policy requires, and the card shows both choices
  /// before anything at all is tapped.
  final VoidCallback onAgree;
  final VoidCallback onDecline;

  @override
  Widget build(BuildContext context) {
    return _Panel(
      palette: palette,
      padding: const EdgeInsets.all(22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Coast uses Accessibility',
            style: TextStyle(
              color: palette.text,
              fontSize: 19,
              fontWeight: FontWeight.w700,
              letterSpacing: -0.3,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            "To scroll feeds for you, Coast uses Android's Accessibility "
            'service to:\n'
            '\u2022  read the screen, only to recognise when a supported '
            'video feed (TikTok, Reels, Shorts) is open\n'
            '\u2022  perform the scroll gesture on your behalf\n\n'
            'While the service is on, Coast could technically observe the '
            'content of your screen. Coast does not collect, store, or share '
            'any of it \u2014 nothing leaves this device.\n\n'
            "If you agree, Android's Accessibility settings will open so you "
            'can turn Coast on.',
            style: TextStyle(color: palette.muted, fontSize: 14, height: 1.5),
          ),
          const SizedBox(height: 18),
          Row(
            children: [
              Expanded(
                child: Material(
                  color: Colors.transparent,
                  borderRadius: BorderRadius.circular(14),
                  child: InkWell(
                    onTap: onDecline,
                    borderRadius: BorderRadius.circular(14),
                    child: Container(
                      height: 50,
                      decoration: BoxDecoration(
                        border: Border.all(color: palette.surfaceAlt, width: 2),
                        borderRadius: BorderRadius.circular(14),
                      ),
                      child: Center(
                        child: Text(
                          'No thanks',
                          style: TextStyle(
                            color: palette.muted,
                            fontSize: 15,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                flex: 2,
                child: Material(
                  color: Palette.accent,
                  borderRadius: BorderRadius.circular(14),
                  child: InkWell(
                    onTap: onAgree,
                    borderRadius: BorderRadius.circular(14),
                    child: const SizedBox(
                      height: 50,
                      child: Center(
                        child: Text(
                          'I agree',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: 15,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            'Accessibility \u2192 Installed apps \u2192 Coast',
            style: TextStyle(color: palette.muted, fontSize: 12.5),
          ),
        ],
      ),
    );
  }
}

/// Shown after a decline. Nothing was enabled, and saying so out loud is what
/// distinguishes a real choice from a nag loop.
class _DeclinedCard extends StatelessWidget {
  const _DeclinedCard({required this.palette, required this.onReview});

  final Palette palette;
  final VoidCallback onReview;

  @override
  Widget build(BuildContext context) {
    return _Panel(
      palette: palette,
      padding: const EdgeInsets.all(22),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Setup declined',
            style: TextStyle(
              color: palette.text,
              fontSize: 19,
              fontWeight: FontWeight.w700,
              letterSpacing: -0.3,
            ),
          ),
          const SizedBox(height: 10),
          Text(
            'Nothing was enabled. Coast cannot scroll feeds without the '
            'Accessibility service, so the app will wait here.',
            style: TextStyle(color: palette.muted, fontSize: 14, height: 1.5),
          ),
          const SizedBox(height: 18),
          Material(
            color: palette.surfaceAlt,
            borderRadius: BorderRadius.circular(14),
            child: InkWell(
              onTap: onReview,
              borderRadius: BorderRadius.circular(14),
              child: SizedBox(
                height: 50,
                width: double.infinity,
                child: Center(
                  child: Text(
                    'Review what Coast needs',
                    style: TextStyle(
                      color: palette.text,
                      fontSize: 15,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _StartingCard extends StatelessWidget {
  const _StartingCard({required this.palette});

  final Palette palette;

  @override
  Widget build(BuildContext context) {
    return _Panel(
      palette: palette,
      child: Row(
        children: [
          const SizedBox(
            width: 20,
            height: 20,
            child: CircularProgressIndicator(
              strokeWidth: 2.4,
              color: Palette.accent,
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Text(
              'Starting up',
              style: TextStyle(
                color: palette.text,
                fontSize: 16,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
