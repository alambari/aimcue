import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:async';

void main() {
  runApp(const AdvancedPoolAimApp());
}

class AdvancedPoolAimApp extends StatelessWidget {
  const AdvancedPoolAimApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Advanced Pool Aim Assistant',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        scaffoldBackgroundColor: const Color(0xFF0F0F23),
        brightness: Brightness.dark,
        cardTheme: CardTheme(
          color: const Color(0xFF1A1A3A),
          elevation: 8,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        ),
      ),
      home: const AdvancedPoolController(),
      debugShowCheckedModeBanner: false,
    );
  }
}

class AdvancedPoolController extends StatefulWidget {
  const AdvancedPoolController({super.key});

  @override
  State<AdvancedPoolController> createState() => _AdvancedPoolControllerState();
}

class _AdvancedPoolControllerState extends State<AdvancedPoolController> 
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
  
  // Advanced Settings
  double shotPower = 0.5;
  double spinX = 0.0;
  double spinY = 0.0;
  bool showCushionShots = true;
  bool showPowerIndicator = true;
  
  // Detection Stats
  int poolGamesDetected = 0;
  int sessionsCompleted = 0;
  String lastPoolGameDetected = 'None';
  int totalShotsAnalyzed = 0;
  
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
        assistantStatus = isAssistantActive ? 'Active - Ready for pool games' : 'Inactive';
      });
      if (isAssistantActive) {
        _updateOverlayStatus();
      }
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
          shotPower = (status['shotPower'] ?? 0.5).toDouble();
          spinX = (status['spinX'] ?? 0.0).toDouble();
          spinY = (status['spinY'] ?? 0.0).toDouble();
          
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
          assistantStatus = 'Active - Ready for pool games';
        });
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

  Future<void> _setShotPower(double power) async {
    if (!isAssistantActive) return;
    
    try {
      await platform.invokeMethod('setShotPower', {'power': power});
      setState(() {
        shotPower = power;
      });
    } catch (e) {
      print('Error setting shot power: $e');
    }
  }

  Future<void> _setSpin(double x, double y) async {
    if (!isAssistantActive) return;
    
    try {
      await platform.invokeMethod('setSpin', {'spinX': x, 'spinY': y});
      setState(() {
        spinX = x;
        spinY = y;
      });
    } catch (e) {
      print('Error setting spin: $e');
    }
  }

  Future<void> _toggleCushionShots() async {
    if (!isAssistantActive) return;
    
    try {
      await platform.invokeMethod('toggleCushionShots');
      setState(() {
        showCushionShots = !showCushionShots;
      });
    } catch (e) {
      print('Error toggling cushion shots: $e');
    }
  }

  Future<void> _togglePowerIndicator() async {
    if (!isAssistantActive) return;
    
    try {
      await platform.invokeMethod('togglePowerIndicator');
      setState(() {
        showPowerIndicator = !showPowerIndicator;
      });
    } catch (e) {
      print('Error toggling power indicator: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Advanced Pool Assistant'),
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
            _buildStatusCard(),
            const SizedBox(height: 16),
            
            if (isAssistantActive) ...[
              _buildTableControlCard(),
              const SizedBox(height: 16),
              _buildAdvancedSettingsCard(),
              const SizedBox(height: 16),
              _buildDisplayOptionsCard(),
              const SizedBox(height: 16),
            ],
            
            _buildPermissionCard(),
            const SizedBox(height: 16),
            _buildEnhancedStatsCard(),
            const SizedBox(height: 16),
            _buildAdvancedFeaturesCard(),
            const SizedBox(height: 16),
            _buildAdvancedInstructionsCard(),
            const SizedBox(height: 16),
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

// Extension methods untuk _AdvancedPoolControllerState class
// Letakkan semua widget methods di sini

  Widget _buildStatusCard() {
    return Card(
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
                const Text(
                  'Advanced Pool Assistant Status',
                  style: TextStyle(
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

  Widget _buildTableControlCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Table Setup & Control',
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
                      padding: const EdgeInsets.symmetric(vertical: 14),
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
                      padding: const EdgeInsets.symmetric(vertical: 14),
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
                        isResizeMode ? Icons.crop_free : Icons.visibility,
                        color: isResizeMode ? Colors.orange : Colors.blue,
                        size: 16,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        'Mode: ${isResizeMode ? "Resize Active" : "Ready for Aiming"}',
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
                        'Table: ${isTableLocked ? "Locked & Ready" : "Setup Mode"}',
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
                          'Size: ${tableRect['width']?.toInt() ?? 0}x${tableRect['height']?.toInt() ?? 0}px',
                          style: const TextStyle(color: Colors.white70, fontSize: 14),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
            
            if (isResizeMode || !isTableLocked) ...[
              const SizedBox(height: 16),
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
                          'Table Setup Mode:',
                          style: TextStyle(color: Colors.orange, fontSize: 14, fontWeight: FontWeight.bold),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    const Text(
                      '• Open your pool game\n'
                      '• Drag corner handles to resize table\n'
                      '• Drag center handle to move table\n'
                      '• Lock table when positioned correctly',
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

  Widget _buildAdvancedSettingsCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Shot Controls',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            
            Row(
              children: [
                const Icon(Icons.flash_on, color: Colors.yellow, size: 20),
                const SizedBox(width: 8),
                const Text('Power:', style: TextStyle(color: Colors.white, fontSize: 16)),
                const SizedBox(width: 16),
                Expanded(
                  child: Slider(
                    value: shotPower,
                    min: 0.0,
                    max: 1.0,
                    divisions: 20,
                    label: '${(shotPower * 100).round()}%',
                    activeColor: shotPower < 0.5 ? Colors.green : shotPower < 0.8 ? Colors.yellow : Colors.red,
                    onChanged: (value) {
                      _setShotPower(value);
                    },
                  ),
                ),
                Text(
                  '${(shotPower * 100).round()}%',
                  style: TextStyle(
                    color: shotPower < 0.5 ? Colors.green : Colors.red,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            
            const SizedBox(height: 20),
            
            const Text(
              'Cue Ball Spin:',
              style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w500),
            ),
            const SizedBox(height: 12),
            
            Row(
              children: [
                const Icon(Icons.swap_horiz, color: Colors.cyan, size: 20),
                const SizedBox(width: 8),
                const Text('Left/Right:', style: TextStyle(color: Colors.white70, fontSize: 14)),
                const SizedBox(width: 8),
                Expanded(
                  child: Slider(
                    value: spinX,
                    min: -1.0,
                    max: 1.0,
                    divisions: 20,
                    label: spinX == 0 ? 'Center' : spinX < 0 ? 'Left ${(-spinX * 100).round()}%' : 'Right ${(spinX * 100).round()}%',
                    activeColor: Colors.cyan,
                    onChanged: (value) {
                      _setSpin(value, spinY);
                    },
                  ),
                ),
                Text(
                  spinX == 0 ? 'Center' : spinX < 0 ? 'L${(-spinX * 100).round()}' : 'R${(spinX * 100).round()}',
                  style: const TextStyle(color: Colors.cyan, fontSize: 12),
                ),
              ],
            ),
            
            Row(
              children: [
                const Icon(Icons.swap_vert, color: Colors.purple, size: 20),
                const SizedBox(width: 8),
                const Text('Top/Bottom:', style: TextStyle(color: Colors.white70, fontSize: 14)),
                const SizedBox(width: 8),
                Expanded(
                  child: Slider(
                    value: spinY,
                    min: -1.0,
                    max: 1.0,
                    divisions: 20,
                    label: spinY == 0 ? 'Center' : spinY < 0 ? 'Top ${(-spinY * 100).round()}%' : 'Bottom ${(spinY * 100).round()}%',
                    activeColor: Colors.purple,
                    onChanged: (value) {
                      _setSpin(spinX, value);
                    },
                  ),
                ),
                Text(
                  spinY == 0 ? 'Center' : spinY < 0 ? 'T${(-spinY * 100).round()}' : 'B${(spinY * 100).round()}',
                  style: const TextStyle(color: Colors.purple, fontSize: 12),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDisplayOptionsCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Display Options',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            
            SwitchListTile(
              title: const Text('Show Cushion Shots', style: TextStyle(color: Colors.white)),
              subtitle: const Text('Display bank shot predictions', style: TextStyle(color: Colors.white60, fontSize: 12)),
              value: showCushionShots,
              activeColor: Colors.cyan,
              onChanged: (value) {
                _toggleCushionShots();
              },
              secondary: const Icon(Icons.timeline, color: Colors.cyan),
            ),
            
            SwitchListTile(
              title: const Text('Show Power Indicator', style: TextStyle(color: Colors.white)),
              subtitle: const Text('Display power bar on overlay', style: TextStyle(color: Colors.white60, fontSize: 12)),
              value: showPowerIndicator,
              activeColor: Colors.yellow,
              onChanged: (value) {
                _togglePowerIndicator();
              },
              secondary: const Icon(Icons.battery_charging_full, color: Colors.yellow),
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
                        'Permission required to show advanced aim assistance over pool games',
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

  Widget _buildEnhancedStatsCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Session Statistics',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            
            GridView.count(
              crossAxisCount: 2,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              childAspectRatio: 1.5,
              mainAxisSpacing: 12,
              crossAxisSpacing: 12,
              children: [
                _buildStatCard('Games\nDetected', poolGamesDetected.toString(), Colors.blue, Icons.sports_esports),
                _buildStatCard('Sessions\nCompleted', sessionsCompleted.toString(), Colors.green, Icons.check_circle),
                _buildStatCard('Last Game', lastPoolGameDetected == 'None' ? 'None' : lastPoolGameDetected.split(' ')[0], Colors.purple, Icons.history),
                _buildStatCard('Shots\nAnalyzed', totalShotsAnalyzed.toString(), Colors.orange, Icons.my_location),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatCard(String label, String value, Color color, IconData icon) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withOpacity(0.3)),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
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
              fontSize: 11,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAdvancedFeaturesCard() {
    final features = [
      {'icon': Icons.touch_app, 'title': 'Touch-Based Aiming', 'desc': 'Tap and drag to set aim direction'},
      {'icon': Icons.timeline, 'title': 'Multi-Cushion Prediction', 'desc': 'Up to 3 bank shot reflections'},
      {'icon': Icons.flash_on, 'title': 'Power & Spin Control', 'desc': 'Adjust shot power and cue ball spin'},
      {'icon': Icons.crop_free, 'title': 'Manual Table Setup', 'desc': 'Resize and position table manually'},
      {'icon': Icons.visibility, 'title': 'Real-Time Visualization', 'desc': 'Live trajectory and impact prediction'},
      {'icon': Icons.save, 'title': 'Persistent Settings', 'desc': 'Saves table position and preferences'},
    ];

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Advanced Features',
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

  Widget _buildAdvancedInstructionsCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'How to Use Advanced Assistant',
              style: TextStyle(
                color: Colors.white,
                fontSize: 18,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 16),
            _buildInstructionStep('1', 'Grant overlay permission if needed'),
            _buildInstructionStep('2', 'Start Assistant and open your pool game'),
            _buildInstructionStep('3', 'Use "Resize Table" to fit overlay to game table'),
            _buildInstructionStep('4', 'Lock table position when aligned properly'),
            _buildInstructionStep('5', 'Adjust power, spin, and display options'),
            _buildInstructionStep('6', 'Tap and drag on table to aim shots'),
            _buildInstructionStep('7', 'View trajectory, cushion shots, and impact points'),
            _buildInstructionStep('8', 'Return here to adjust settings anytime'),
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
            
            if (isAssistantActive && overlayStatus.isNotEmpty) ...[
              const SizedBox(height: 12),
              const Divider(color: Colors.white24),
              const SizedBox(height: 12),
              const Text(
                'Overlay Status',
                style: TextStyle(color: Colors.white60, fontSize: 12),
              ),
              const SizedBox(height: 8),
              Text(
                'Active: ${overlayStatus['isActive'] ?? false}\n'
                'Resize Mode: ${overlayStatus['isResizeMode'] ?? false}\n'
                'Table Locked: ${overlayStatus['isTableLocked'] ?? true}\n'
                'Power: ${(shotPower * 100).round()}% | Spin: ${(spinX * 100).round()}, ${(spinY * 100).round()}',
                style: const TextStyle(color: Colors.white70, fontSize: 11, fontFamily: 'monospace'),
              ),
            ],
          ],
        ),
      ),
    );
  }

// CLOSING BRACKET FOR THE CLASS
}