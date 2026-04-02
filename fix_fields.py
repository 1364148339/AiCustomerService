import os
import re

base_dir = r"d:\wq\AiPro\AiCustomerService\backend\aimacrodroid-server\src\main\java\com\aimacrodroid"

replacements = {
    # Alert
    r'\balert\.getStepId\(': 'alert.getStepInstanceId(',
    r'\balert\.setStepId\(': 'alert.setStepInstanceId(',
    r'\bexisting\.getStepId\(': 'existing.getStepInstanceId(',
    r'\bexisting\.setStepId\(': 'existing.setStepInstanceId(',
    r'Alert::getStepId\b': 'Alert::getStepInstanceId',
    
    r'\balert\.getLevel\(': 'alert.getAlertLevel(',
    r'\balert\.setLevel\(': 'alert.setAlertLevel(',
    r'\bexisting\.getLevel\(': 'existing.getAlertLevel(',
    r'\bexisting\.setLevel\(': 'existing.setAlertLevel(',
    r'Alert::getLevel\b': 'Alert::getAlertLevel',
    
    r'\balert\.getStatus\(': 'alert.getAlertStatus(',
    r'\balert\.setStatus\(': 'alert.setAlertStatus(',
    r'\bexisting\.getStatus\(': 'existing.getAlertStatus(',
    r'\bexisting\.setStatus\(': 'existing.setAlertStatus(',
    r'Alert::getStatus\b': 'Alert::getAlertStatus',
    
    r'\balert\.getDetail\(': 'alert.getDetailJson(',
    r'\balert\.setDetail\(': 'alert.setDetailJson(',
    r'\bexisting\.getDetail\(': 'existing.getDetailJson(',
    r'\bexisting\.setDetail\(': 'existing.setDetailJson(',
    r'Alert::getDetail\b': 'Alert::getDetailJson',

    # Device
    r'Device::getDeviceId\b': 'Device::getDeviceCode',
    r'\bdevice\.getDeviceId\(': 'device.getDeviceCode(',
    r'\bdevice\.setDeviceId\(': 'device.setDeviceCode(',
    r'\bcurrent\.getDeviceId\(': 'current.getDeviceCode(',
    r'\bcurrent\.setDeviceId\(': 'current.setDeviceCode(',
    r'\bexistingDevice\.getDeviceId\(': 'existingDevice.getDeviceCode(',
    r'\bexistingDevice\.setDeviceId\(': 'existingDevice.setDeviceCode(',
    
    r'\bdevice\.getCapabilities\(': 'device.getCapabilityJson(',
    r'\bdevice\.setCapabilities\(': 'device.setCapabilityJson(',
    r'\bcurrent\.getCapabilities\(': 'current.getCapabilityJson(',
    r'\bcurrent\.setCapabilities\(': 'current.setCapabilityJson(',
    r'\bexistingDevice\.getCapabilities\(': 'existingDevice.getCapabilityJson(',
    r'\bexistingDevice\.setCapabilities\(': 'existingDevice.setCapabilityJson(',
    r'Device::getCapabilities\b': 'Device::getCapabilityJson',
    
    r'\bdevice\.getLastHeartbeatTime\(': 'device.getLastSeenAt(',
    r'\bdevice\.setLastHeartbeatTime\(': 'device.setLastSeenAt(',
    r'\bcurrent\.getLastHeartbeatTime\(': 'current.getLastSeenAt(',
    r'\bcurrent\.setLastHeartbeatTime\(': 'current.setLastSeenAt(',
    r'\bexistingDevice\.getLastHeartbeatTime\(': 'existingDevice.getLastSeenAt(',
    r'\bexistingDevice\.setLastHeartbeatTime\(': 'existingDevice.setLastSeenAt(',
    r'Device::getLastHeartbeatTime\b': 'Device::getLastSeenAt',
    
    r'\bdevice\.getToken\(': 'device.getTokenHash(',
    r'\bdevice\.setToken\(': 'device.setTokenHash(',
    r'\bcurrent\.getToken\(': 'current.getTokenHash(',
    r'\bcurrent\.setToken\(': 'current.setTokenHash(',
    r'\bexistingDevice\.getToken\(': 'existingDevice.getTokenHash(',
    r'\bexistingDevice\.setToken\(': 'existingDevice.setTokenHash(',
    r'Device::getToken\b': 'Device::getTokenHash',
    
    r'\bdevice\.getStatus\(': 'device.getDeviceStatus(',
    r'\bdevice\.setStatus\(': 'device.setDeviceStatus(',
    r'\bcurrent\.getStatus\(': 'current.getDeviceStatus(',
    r'\bcurrent\.setStatus\(': 'current.setDeviceStatus(',
    r'\bexistingDevice\.getStatus\(': 'existingDevice.getDeviceStatus(',
    r'\bexistingDevice\.setStatus\(': 'existingDevice.setDeviceStatus(',
    r'Device::getStatus\b': 'Device::getDeviceStatus',

    # DeviceReadiness
    r'\breadiness\.getShizukuRunning\(': 'readiness.getIsShizukuAvailable(',
    r'\breadiness\.setShizukuRunning\(': 'readiness.setIsShizukuAvailable(',
    r'DeviceReadiness::getShizukuRunning\b': 'DeviceReadiness::getIsShizukuAvailable',
    
    r'\breadiness\.getOverlayGranted\(': 'readiness.getIsOverlayGranted(',
    r'\breadiness\.setOverlayGranted\(': 'readiness.setIsOverlayGranted(',
    r'DeviceReadiness::getOverlayGranted\b': 'DeviceReadiness::getIsOverlayGranted',
    
    r'\breadiness\.getKeyboardEnabled\(': 'readiness.getIsKeyboardEnabled(',
    r'\breadiness\.setKeyboardEnabled\(': 'readiness.setIsKeyboardEnabled(',
    r'DeviceReadiness::getKeyboardEnabled\b': 'DeviceReadiness::getIsKeyboardEnabled',
    
    r'\breadiness\.getSseSupported\(': 'readiness.getIsSseSupported(',
    r'\breadiness\.setSseSupported\(': 'readiness.setIsSseSupported(',
    r'DeviceReadiness::getSseSupported\b': 'DeviceReadiness::getIsSseSupported',

    # RunEvent
    r'\bevent\.getStepId\(': 'event.getStepInstanceId(',
    r'\bevent\.setStepId\(': 'event.setStepInstanceId(',
    r'\bretryEvent\.getStepId\(': 'retryEvent.getStepInstanceId(',
    r'\bretryEvent\.setStepId\(': 'retryEvent.setStepInstanceId(',
    r'RunEvent::getStepId\b': 'RunEvent::getStepInstanceId',
    
    r'\bevent\.getStatus\(': 'event.getEventStatus(',
    r'\bevent\.setStatus\(': 'event.setEventStatus(',
    r'\bretryEvent\.getStatus\(': 'retryEvent.getEventStatus(',
    r'\bretryEvent\.setStatus\(': 'retryEvent.setEventStatus(',
    r'RunEvent::getStatus\b': 'RunEvent::getEventStatus',
    
    r'\bevent\.getTrace\(': 'event.getTraceJson(',
    r'\bevent\.setTrace\(': 'event.setTraceJson(',
    r'\bretryEvent\.getTrace\(': 'retryEvent.getTraceJson(',
    r'\bretryEvent\.setTrace\(': 'retryEvent.setTraceJson(',
    r'RunEvent::getTrace\b': 'RunEvent::getTraceJson',
    
    r'\bevent\.getThinking\(': 'event.getThinkingText(',
    r'\bevent\.setThinking\(': 'event.setThinkingText(',
    r'\bretryEvent\.getThinking\(': 'retryEvent.getThinkingText(',
    r'\bretryEvent\.setThinking\(': 'retryEvent.setThinkingText(',
    r'RunEvent::getThinking\b': 'RunEvent::getThinkingText',
    
    r'\bevent\.getSensitiveScreenDetected\(': 'event.getIsSensitiveScreen(',
    r'\bevent\.setSensitiveScreenDetected\(': 'event.setIsSensitiveScreen(',
    r'\bretryEvent\.getSensitiveScreenDetected\(': 'retryEvent.getIsSensitiveScreen(',
    r'\bretryEvent\.setSensitiveScreenDetected\(': 'retryEvent.setIsSensitiveScreen(',
    r'RunEvent::getSensitiveScreenDetected\b': 'RunEvent::getIsSensitiveScreen',
    
    r'\bevent\.getProgress\(': 'event.getProgressJson(',
    r'\bevent\.setProgress\(': 'event.setProgressJson(',
    r'\bretryEvent\.getProgress\(': 'retryEvent.getProgressJson(',
    r'\bretryEvent\.setProgress\(': 'retryEvent.setProgressJson(',
    r'RunEvent::getProgress\b': 'RunEvent::getProgressJson',

    # Snapshot
    r'\bsnapshot\.getElements\(': 'snapshot.getElementJson(',
    r'\bsnapshot\.setElements\(': 'snapshot.setElementJson(',
    r'Snapshot::getElements\b': 'Snapshot::getElementJson',

    # Task
    r'\btask\.getConstraints\(': 'task.getTaskConstraints(',
    r'\btask\.setConstraints\(': 'task.setTaskConstraints(',
    r'Task::getConstraints\b': 'Task::getTaskConstraints',
    
    # TaskDeviceRun
    r'\brun\.getStatus\(': 'run.getRunStatus(',
    r'\brun\.setStatus\(': 'run.setRunStatus(',
    r'TaskDeviceRun::getStatus\b': 'TaskDeviceRun::getRunStatus',
}

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
        
    new_content = content
    for pattern, replacement in replacements.items():
        new_content = re.sub(pattern, replacement, new_content)
        
    if new_content != content:
        print(f"Updated {filepath}")
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)

for root, dirs, files in os.walk(base_dir):
    for file in files:
        if file.endswith('.java'):
            process_file(os.path.join(root, file))

print("Done")
