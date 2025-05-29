import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:async';

void main() {
  runApp(const SmartPoolAimApp());
}

class SmartPoolAimApp extends StatelessWidget {
  const SmartPoolAimApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Smart Pool Aim Assistant',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        scaffoldBackgroundColor: const Color(0xFF1A1A2E),
        brightness: Brightness.dark,
      ),
      home: const SmartPoolController(),
      debugShowCheckedModeBanner: false,
    );
  }
}

class SmartPoolController extends StatefulWidget {
  const SmartPoolController({super.key});

  @override
  State<SmartPoolController> createState() => _SmartPoolControllerState();
}

class _SmartPoolControllerState extends State<SmartPoolController> 
    with WidgetsBindingObserver {
  
  static const platform = MethodChannel('pool_overlay/system');
  
  // Permission & Status
  bool hasOverlayPermission = false;
  bool isAssistantActive = false;
  String currentForegroundApp = 'Unknown';
  String assistantStatus = 'Inactive';
  
  // Overlay Control Status
  bool isResizeMode = false;
  bool isTableLocked = true;
  Map<String, dynamic> tableRect = {};
  Map<String, dynamic> overlayStatus = {};
  
  // Detection Stats
  int poolGamesDetected = 0;
  int sessionsCompleted = 0;
  String lastPoolGameDetected = 'None';
  
  Timer? statusTimer;
  Timer? overlayStatusTimer;

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
    overlayStatusTimer?.cancel();
    super.dispose();
  }

  void _initializeApp() async {
    await _checkOverlayPermission();
    _startStatusMonitoring();
    _startOverlayStatusMonitoring();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    super.didChangeAppLifecycleState(state);
    
    if (state == AppLifecycleState.paused && isAssistantActive) {
      setState(() {
        assistantStatus = 'Monitoring background apps...';
      });
    } else if (state == AppLifecycleState.resumed) {
      setState(() {
        assistantStatus = isAssistantActive ? 'Active - Waiting for pool game' : 'Inactive';
      });
      _updateOverlayStatus();
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

  void _startOverlayStatusMonitoring() {
    overlayStatusTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (isAssistantActive) {
        _updateOverlayStatus();
      }
    });
  }

  Future<void> _updateStatus() async {
    if (!isAssistantActive) return;
    
    try {
      final String? foregroundApp = await platform.invokeMethod('getForegroundApp');
      if (foregroundApp != null) {
        setState(() {
          currentForegroundApp = foregroundApp;
          
          if (_isPoolGame(foregroundApp)) {
            assistantStatus = 'Active - Analyzing ${_getGameName(foregroundApp)}';
            lastPoolGameDetected = _getGameName(foregroundApp);
            if (poolGamesDetected == 0) poolGamesDetected = 1;
          } else {
            assistantStatus = 'Monitoring - Waiting for pool game';
          }
        });
      }
    } catch (e) {
      print('Error updating status: $e');
    }
  }

  Future<void> _updateOverlayStatus() async {
    if (!isAssistantActive) return;
    
    try {
      final Map<dynamic, dynamic>? status = await platform.invokeMethod('getOverlayStatus');
      if (status != null) {
        setState(() {
          overlayStatus = Map<String, dynamic>.from(status);
          isResizeMode = status['isResizeMode'] ?? false;
          isTableLocked = status['isTableLocked'] ?? true;
          if (status['tableRect'] != null) {
            tableRect = Map<String, dynamic>.from(status['tableRect']);
          }
        });
      }
    } catch (e) {
      print('Error updating overlay status: $e');
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

  Future<void> _toggleAssistant() async {
    if (!hasOverlayPermission) {
      await _requestOverlayPermission();
      return;
    }

    try {
      if (isAssistantActive) {
        await platform.invokeMethod('hideOverlay');
        setState(() {
          isAssistantActive = false;
          assistantStatus = 'Inactive';
          sessionsCompleted++;
          overlayStatus.clear();
          tableRect.clear();
        });
      } else {
        await platform.invokeMethod('showOverlay');
        setState(() {
          isAssistantActive = true;
          assistantStatus = 'Active - Waiting for pool game';
        });
        // Wait a bit then update overlay status
        await Future.delayed(const Duration(milliseconds: 500));
        _updateOverlayStatus();
      }
    } catch (e) {
      print('Error toggling assistant: $e');
    }
  }

  Future<void> _toggleResizeMode() async {
    if (!isAssistantActive) return;
    
    try {
      await platform.invokeMethod('toggleResizeMode');
      await Future.delayed(const Duration(milliseconds: 200));
      _updateOverlayStatus();
    } catch (e) {
      print('Error toggling resize mode: $e');
    }
  }

  Future<void> _toggleTableLock() async {
    if (!isAssistantActive) return;
    
    try {
      await platform.invokeMethod('toggleTableLock');
      await Future.delayed(const Duration(milliseconds: 200));
      _updateOverlayStatus();
    } catch (e) {
      print('Error toggling table lock: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Smart Pool Aim Assistant'),
        backgroundColor: const Color(0xFF16213E),
        elevation: 0,
        actions: [
          IconButton(
            icon: Icon(
              isAssistantActive ? Icons.visibility : Icons.visibility_off,
              color: isAssistantActive ? Colors.green : Colors.grey,
            ),
            onPressed: _toggleAssistant,
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            // Status Card
            _buildStatusCard(),
            const SizedBox(height: 16),
            
            // Overlay Control Card (only show when active)
            if (isAssistantActive) ...[
              _buildOverlayControlCard(),
              const SizedBox(height: 16),
            ],
            
            // Permission Card
            _buildPermissionCard(),
            const SizedBox(height: 16),
            
            // Detection Stats Card
            _buildDetectionStatsCard(),
            const SizedBox(height: 16),
            
            // Features Card
            _buildFeaturesCard(),
            const SizedBox(height: 16),
            
            // Instructions Card
            _buildInstructionsCard(),
            const SizedBox(height: 16),
            
            // Current App Info Card
            _buildCurrentAppCard(),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _toggleAssistant,
        backgroundColor: isAssistantActive ? Colors.red[600] : Colors.green[600],
        icon: Icon(isAssistantActive ? Icons.stop : Icons.play_arrow),
        label: Text(isAssistantActive ? 'Stop Assistant' : 'Start Assistant'),
      ),
    );
  }

  Widget _buildOverlayControlCard() {
    return Card(
      color: const Color(0xFF0F3460),
      elevation: 8,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Overlay Controls',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            
            // Control Buttons Row
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
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _toggleTableLock,
                    icon: Icon(isTableLocked ? Icons.lock : Icons.lock_open),
                    label: Text(isTableLocked ? 'Unlock' : 'Lock'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: isTableLocked ? Colors.red : Colors.green,
                      foregroundColor: Colors.white,
                    ),
                  ),
                ),
              ],
            ),
            
            const SizedBox(height: 16),
            
            // Status Info
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
                        isResizeMode ? Icons.crop_free : Icons.visibility,
                        color: isResizeMode ? Colors.orange : Colors.blue,
                        size: 16,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        'Mode: ${isResizeMode ? "Resize Active" : "Normal Display"}',
                        style: const TextStyle(color: Colors.white70, fontSize: 14),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Icon(
                        isTableLocked ? Icons.lock : Icons.lock_open,
                        color: isTableLocked ? Colors.red : Colors.green,
                        size: 16,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        'Table: ${isTableLocked ? "Locked" : "Unlocked"}',
                        style: const TextStyle(color: Colors.white70, fontSize: 14),
                      ),
                    ],
                  ),
                  if (tableRect.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        const Icon(Icons.crop_landscape, color: Colors.cyan, size: 16),
                        const SizedBox(width: 8),
                        Text(
                          'Table: ${tableRect['width']?.toInt() ?? 0}x${tableRect['height']?.toInt() ?? 0}',
                          style: const TextStyle(color: Colors.white70, fontSize: 14),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
            
            const SizedBox(height: 12),
            
            // Instructions for resize
            if (isResizeMode || !isTableLocked) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.orange.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.orange.withOpacity(0.3)),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Icon(Icons.info, color: Colors.orange, size: 16),
                        const SizedBox(width: 8),
                        const Text(
                          'Resize Instructions:',
                          style: TextStyle(color: Colors.orange, fontSize: 14, fontWeight: FontWeight.bold),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      '• Go to pool game screen\n'
                      '• Drag corner handles to resize table\n'
                      '• Drag center handle to move table\n'
                      '• Lock table when done',
                      style: TextStyle(color: Colors.orange, fontSize: 12),
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

  Widget _buildStatusCard() {
    return Card(
      color: const Color(0xFF0F3460),
      elevation: 8,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  width: 12,
                  height: 12,
                  decoration: BoxDecoration(
                    color: isAssistantActive ? Colors.green : Colors.red,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 12),
                Text(
                  'Smart Assistant Status',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Text(
              assistantStatus,
              style: TextStyle(
                color: isAssistantActive ? Colors.green[300] : Colors.orange[300],
                fontSize: 16,
                fontWeight: FontWeight.w500,
              ),
            ),
            const SizedBox(height: 12),
            if (isAssistantActive) ...[
              const Divider(color: Colors.white24),
              const SizedBox(height: 12),
              Row(
                children: [
                  const Icon(Icons.smartphone, color: Colors.white70, size: 16),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Monitoring: ${currentForegroundApp.split('.').last}',
                      style: const TextStyle(color: Colors.white70, fontSize: 14),
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildPermissionCard() {
    return Card(
      color: const Color(0xFF0F3460),
      elevation: 8,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'System Permissions',
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
                Expanded(
                  child: Text(
                    'Display over other apps',
                    style: const TextStyle(color: Colors.white, fontSize: 16),
                  ),
                ),
                if (!hasOverlayPermission)
                  TextButton(
                    onPressed: _requestOverlayPermission,
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
                child: Row(
                  children: [
                    const Icon(Icons.warning, color: Colors.orange, size: 20),
                    const SizedBox(width: 8),
                    const Expanded(
                      child: Text(
                        'Permission required to show aim assistance over pool games',
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

  Widget _buildDetectionStatsCard() {
    return Card(
      color: const Color(0xFF0F3460),
      elevation: 8,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Detection Statistics',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildStatItem('Pool Games\nDetected', poolGamesDetected.toString(), Colors.blue),
                _buildStatItem('Sessions\nCompleted', sessionsCompleted.toString(), Colors.green),
                _buildStatItem('Last Game\nDetected', lastPoolGameDetected == 'None' ? '-' : lastPoolGameDetected.split(' ')[0], Colors.purple),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatItem(String label, String value, Color color) {
    return Column(
      children: [
        Text(
          value,
          style: TextStyle(
            color: color,
            fontSize: 24,
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
    );
  }

  Widget _buildFeaturesCard() {
    final features = [
      {'icon': Icons.timeline, 'title': 'Auto Extended Guidelines', 'desc': 'Automatic guideline extension'},
      {'icon': Icons.swap_horiz, 'title': 'Cushion Shot Prediction', 'desc': 'Bank shot trajectory analysis'},
      {'icon': Icons.my_location, 'title': 'Cue Ball Path Prediction', 'desc': 'Post-impact ball movement'},
      {'icon': Icons.crop_free, 'title': 'Manual Table Adjustment', 'desc': 'Resize and position table manually'},
      {'icon': Icons.insights, 'title': 'AI Image Recognition', 'desc': 'Smart game detection technology'},
    ];

    return Card(
      color: const Color(0xFF0F3460),
      elevation: 8,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Smart Features',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            ...features.map((feature) => Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: Colors.blue.withOpacity(0.2),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Icon(
                      feature['icon'] as IconData,
                      color: Colors.blue,
                      size: 20,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          feature['title'] as String,
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 14,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                        Text(
                          feature['desc'] as String,
                          style: const TextStyle(
                            color: Colors.white60,
                            fontSize: 12,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            )),
          ],
        ),
      ),
    );
  }

  Widget _buildInstructionsCard() {
    return Card(
      color: const Color(0xFF0F3460),
      elevation: 8,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'How to Use Smart Assistant',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            _buildInstructionStep('1', 'Grant overlay permission if needed'),
            _buildInstructionStep('2', 'Tap "Start Assistant" to activate monitoring'),
            _buildInstructionStep('3', 'Open any pool/billiard game (8 Ball Pool, etc.)'),
            _buildInstructionStep('4', 'Use "Resize Table" to adjust overlay to match game table'),
            _buildInstructionStep('5', 'Lock table position when properly aligned'),
            _buildInstructionStep('6', 'Smart guidelines will appear during gameplay'),
            _buildInstructionStep('7', 'Return here to stop or adjust assistant'),
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
            decoration: BoxDecoration(
              color: Colors.blue,
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
      color: const Color(0xFF0F3460),
      elevation: 8,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
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
                      Text(
                        'Foreground App',
                        style: const TextStyle(color: Colors.white60, fontSize: 12),
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
            
            // Debug info for overlay status (only when active)
            if (isAssistantActive && overlayStatus.isNotEmpty) ...[
              const SizedBox(height: 12),
              const Divider(color: Colors.white24),
              const SizedBox(height: 12),
              Text(
                'Overlay Debug Info',
                style: const TextStyle(color: Colors.white60, fontSize: 12),
              ),
              const SizedBox(height: 8),
              Text(
                'Active: ${overlayStatus['isActive'] ?? false}\n'
                'Resize Mode: ${overlayStatus['isResizeMode'] ?? false}\n'
                'Table Locked: ${overlayStatus['isTableLocked'] ?? true}',
                style: const TextStyle(color: Colors.white70, fontSize: 11, fontFamily: 'monospace'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}