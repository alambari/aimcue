import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:async';

void main() {
  runApp(const SimplePoolGuidelineApp());
}

class SimplePoolGuidelineApp extends StatelessWidget {
  const SimplePoolGuidelineApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Simple Pool Guideline',
      theme: ThemeData(
        primarySwatch: Colors.green,
        scaffoldBackgroundColor: const Color(0xFF0D1B2A),
        brightness: Brightness.dark,
        cardTheme: CardTheme(
          color: const Color(0xFF1B263B),
          elevation: 6,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
      ),
      home: const SimplePoolController(),
      debugShowCheckedModeBanner: false,
    );
  }
}

class SimplePoolController extends StatefulWidget {
  const SimplePoolController({super.key});

  @override
  State<SimplePoolController> createState() => _SimplePoolControllerState();
}

class _SimplePoolControllerState extends State<SimplePoolController> 
    with WidgetsBindingObserver {
  
  static const platform = MethodChannel('pool_overlay/system');
  
  // Core Status
  bool hasOverlayPermission = false;
  bool isGuidelineActive = false;
  String currentForegroundApp = 'Unknown';
  String guidelineStatus = 'Inactive';
  
  // Table Control Status
  bool isResizeMode = false;
  bool isTableLocked = true;
  Map<String, dynamic> tableRect = {};
  
  // Simple Stats
  int poolGamesDetected = 0;
  int sessionsCompleted = 0;
  
  Timer? statusTimer;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _initializeApp();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    statusTimer?.cancel();
    super.dispose();
  }

  void _initializeApp() async {
    await _checkOverlayPermission();
    _startStatusMonitoring();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    
    if (state == AppLifecycleState.paused && isGuidelineActive) {
      setState(() {
        guidelineStatus = 'Monitoring in background...';
      });
    } else if (state == AppLifecycleState.resumed) {
      setState(() {
        guidelineStatus = isGuidelineActive ? 'Active - Ready for pool games' : 'Inactive';
      });
    }
  }

  Future<void> _checkOverlayPermission() async {
    try {
      final bool result = await platform.invokeMethod('hasOverlayPermission');
      setState(() {
        hasOverlayPermission = result;
      });
    } catch (e) {
      print('Error checking overlay permission: $e');
    }
  }

  Future<void> _requestOverlayPermission() async {
    try {
      await platform.invokeMethod('requestOverlayPermission');
      await Future.delayed(const Duration(seconds: 2));
      await _checkOverlayPermission();
    } catch (e) {
      print('Error requesting overlay permission: $e');
    }
  }

  void _startStatusMonitoring() {
    statusTimer = Timer.periodic(const Duration(seconds: 2), (timer) {
      _updateStatus();
    });
  }

  Future<void> _updateStatus() async {
    if (!isGuidelineActive) return;
    
    try {
      final String? foregroundApp = await platform.invokeMethod('getForegroundApp');
      if (foregroundApp != null) {
        setState(() {
          currentForegroundApp = foregroundApp;
          
          if (_isPoolGame(foregroundApp)) {
            guidelineStatus = 'Active - Detecting ${_getGameName(foregroundApp)}';
            if (poolGamesDetected == 0) poolGamesDetected = 1;
          } else {
            guidelineStatus = 'Monitoring - Waiting for pool game';
          }
        });
      }

      // Update table status
      final Map<dynamic, dynamic>? overlayStatus = await platform.invokeMethod('getOverlayStatus');
      if (overlayStatus != null) {
        setState(() {
          isResizeMode = overlayStatus['isResizeMode'] ?? false;
          isTableLocked = overlayStatus['isTableLocked'] ?? true;
          
          if (overlayStatus['tableRect'] != null) {
            tableRect = Map<String, dynamic>.from(overlayStatus['tableRect']);
          }
        });
      }
    } catch (e) {
      print('Error updating status: $e');
    }
  }

  bool _isPoolGame(String packageName) {
    return packageName.contains('pool') || 
           packageName.contains('billiard') || 
           packageName.contains('miniclip') ||
           packageName.contains('snooker');
  }

  String _getGameName(String packageName) {
    if (packageName.contains('miniclip')) return '8 Ball Pool';
    if (packageName.contains('billiard')) return 'Billiards Game';
    if (packageName.contains('snooker')) return 'Snooker Game';
    return 'Pool Game';
  }

  Future<void> _toggleGuideline() async {
    if (!hasOverlayPermission) {
      await _requestOverlayPermission();
      return;
    }

    try {
      if (isGuidelineActive) {
        await platform.invokeMethod('hideOverlay');
        setState(() {
          isGuidelineActive = false;
          guidelineStatus = 'Inactive';
          sessionsCompleted++;
          tableRect.clear();
        });
      } else {
        await platform.invokeMethod('showOverlay');
        setState(() {
          isGuidelineActive = true;
          guidelineStatus = 'Active - Ready for pool games';
        });
      }
    } catch (e) {
      print('Error toggling guideline: $e');
    }
  }

  Future<void> _toggleResizeMode() async {
    if (!isGuidelineActive) return;
    
    try {
      await platform.invokeMethod('toggleResizeMode');
      await Future.delayed(const Duration(milliseconds: 200));
    } catch (e) {
      print('Error toggling resize mode: $e');
    }
  }

  Future<void> _toggleTableLock() async {
    if (!isGuidelineActive) return;
    
    try {
      await platform.invokeMethod('toggleTableLock');
      await Future.delayed(const Duration(milliseconds: 200));
    } catch (e) {
      print('Error toggling table lock: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('🎱 Simple Pool Guideline'),
        backgroundColor: const Color(0xFF415A77),
        elevation: 0,
        centerTitle: true,
        actions: [
          IconButton(
            icon: Icon(
              isGuidelineActive ? Icons.visibility : Icons.visibility_off,
              color: isGuidelineActive ? Colors.green : Colors.grey,
            ),
            onPressed: _toggleGuideline,
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            _buildMainStatusCard(),
            const SizedBox(height: 16),
            
            if (isGuidelineActive) ...[
              _buildTableSetupCard(),
              const SizedBox(height: 16),
            ],
            
            _buildPermissionCard(),
            const SizedBox(height: 16),
            _buildSimpleStatsCard(),
            const SizedBox(height: 16),
            _buildInstructionsCard(),
            const SizedBox(height: 16),
            _buildCurrentAppCard(),
            const SizedBox(height: 80), // Space for FAB
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _toggleGuideline,
        backgroundColor: isGuidelineActive ? Colors.red[600] : Colors.green[600],
        icon: Icon(isGuidelineActive ? Icons.stop : Icons.play_arrow),
        label: Text(isGuidelineActive ? 'Stop Guideline' : 'Start Guideline'),
      ),
    );
  }

  Widget _buildMainStatusCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 14,
                  height: 14,
                  decoration: BoxDecoration(
                    color: isGuidelineActive ? Colors.green : Colors.red,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 12),
                const Text(
                  'Pool Guideline Status',
                  style: TextStyle(
                    color: Colors.white,
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              guidelineStatus,
              style: TextStyle(
                color: isGuidelineActive ? Colors.green[300] : Colors.orange[300],
                fontSize: 16,
                fontWeight: FontWeight.w500,
              ),
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.green.withOpacity(0.1),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.green.withOpacity(0.3)),
              ),
              child: const Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.timeline, color: Colors.green, size: 16),
                      SizedBox(width: 8),
                      Text(
                        'Simple Guideline Features:',
                        style: TextStyle(color: Colors.green, fontSize: 14, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  SizedBox(height: 8),
                  Text(
                    '• White trajectory line from cue ball\n'
                    '• Extended dashed guideline for aiming\n'
                    '• Auto-detect pool games (8 Ball Pool, etc)\n'
                    '• Manual table positioning',
                    style: TextStyle(color: Colors.green, fontSize: 12),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTableSetupCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Table Setup',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            
            Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _toggleResizeMode,
                    icon: Icon(isResizeMode ? Icons.check : Icons.crop_free),
                    label: Text(isResizeMode ? 'Exit Resize' : 'Resize Table'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: isResizeMode ? Colors.orange : Colors.blue,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _toggleTableLock,
                    icon: Icon(isTableLocked ? Icons.lock : Icons.lock_open),
                    label: Text(isTableLocked ? 'Locked' : 'Unlocked'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: isTableLocked ? Colors.green : Colors.red,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                  ),
                ),
              ],
            ),
            
            const SizedBox(height: 16),
            
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.black.withOpacity(0.3),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Column(
                children: [
                  Row(
                    children: [
                      Icon(
                        isTableLocked ? Icons.check_circle : Icons.settings,
                        color: isTableLocked ? Colors.green : Colors.orange,
                        size: 16,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Status: ${isTableLocked ? "Ready for guideline" : "Setup mode"}',
                          style: const TextStyle(color: Colors.white70, fontSize: 14),
                        ),
                      ),
                    ],
                  ),
                  if (tableRect.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        const Icon(Icons.crop_landscape, color: Colors.cyan, size: 16),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            'Table Size: ${tableRect['width']?.toInt() ?? 0}x${tableRect['height']?.toInt() ?? 0}px',
                            style: const TextStyle(color: Colors.white70, fontSize: 14),
                          ),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'System Permission',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Icon(
                  hasOverlayPermission ? Icons.check_circle : Icons.error,
                  color: hasOverlayPermission ? Colors.green : Colors.red,
                  size: 20,
                ),
                const SizedBox(width: 12),
                const Expanded(
                  child: Text(
                    'Display over other apps',
                    style: TextStyle(color: Colors.white, fontSize: 16),
                  ),
                ),
                if (!hasOverlayPermission)
                  ElevatedButton(
                    onPressed: _requestOverlayPermission,
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.orange),
                    child: const Text('Grant'),
                  ),
              ],
            ),
            if (!hasOverlayPermission) ...[
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.orange.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.orange.withOpacity(0.3)),
                ),
                child: const Row(
                  children: [
                    Icon(Icons.warning, color: Colors.orange, size: 20),
                    SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Permission required to show guideline over pool games',
                        style: TextStyle(color: Colors.orange, fontSize: 12),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildSimpleStatsCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Simple Statistics',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            
            Row(
              children: [
                Expanded(
                  child: _buildSimpleStatCard('Pool Games\nDetected', poolGamesDetected.toString(), Colors.blue, Icons.sports_esports),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: _buildSimpleStatCard('Sessions\nCompleted', sessionsCompleted.toString(), Colors.green, Icons.check_circle),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSimpleStatCard(String label, String value, Color color, IconData icon) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Column(
        children: [
          Icon(icon, color: color, size: 24),
          const SizedBox(height: 8),
          Text(
            value,
            style: TextStyle(
              color: color,
              fontSize: 20,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Colors.white70,
              fontSize: 12,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInstructionsCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'How to Use',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            _buildInstructionStep('1', 'Grant overlay permission'),
            _buildInstructionStep('2', 'Start guideline and open pool game'),
            _buildInstructionStep('3', 'Use "Resize Table" to position overlay'),
            _buildInstructionStep('4', 'Lock table when positioned correctly'),
            _buildInstructionStep('5', 'Tap and drag on table to see guideline'),
            _buildInstructionStep('6', 'White line shows trajectory direction'),
          ],
        ),
      ),
    );
  }

  Widget _buildInstructionStep(String number, String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Container(
            width: 24,
            height: 24,
            decoration: const BoxDecoration(
              color: Colors.green,
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Text(
                number,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(color: Colors.white70, fontSize: 14),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCurrentAppCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Current App Detection',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Icon(
                  _isPoolGame(currentForegroundApp) ? Icons.sports_esports : Icons.smartphone,
                  color: _isPoolGame(currentForegroundApp) ? Colors.green : Colors.white70,
                  size: 20,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Foreground App',
                        style: TextStyle(color: Colors.white60, fontSize: 12),
                      ),
                      Text(
                        currentForegroundApp.split('.').last,
                        style: const TextStyle(color: Colors.white, fontSize: 14),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: _isPoolGame(currentForegroundApp) ? Colors.green.withOpacity(0.2) : Colors.grey.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    _isPoolGame(currentForegroundApp) ? 'Pool Game' : 'Other App',
                    style: TextStyle(
                      color: _isPoolGame(currentForegroundApp) ? Colors.green : Colors.white60,
                      fontSize: 10,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}