package com.udacity.catpoint.security.service;


import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    private SecurityService securityService;

    private Sensor sensor;

    private final String random = UUID.randomUUID().toString();

    @Mock
    private StatusListener statusListener;

    @Mock
    private SecurityRepository securityRepository;

    @Mock
    private ImageService imageService;

    @BeforeEach
    void init() {
        securityService = new SecurityService(securityRepository, imageService);
        sensor = new Sensor(random, SensorType.DOOR);
    }

    private Set<Sensor> allSensorsAsASet(int count, boolean status) {
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i < count; i++) {
            sensors.add(new Sensor(random, SensorType.DOOR));
        }

        sensors.forEach(sensor -> sensor.setActive(status));
        return sensors;
    }

    //1,If alarm is armed and a sensor becomes activated, put the system into pending alarm status. 待报警
    @Test
    void whenAlarmArmedAndSensorActivated_statusShouldBePending() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    //2,If alarm is armed and a sensor becomes activated and the system is already pending alarm, set off the alarm.报警
    @Test
    void whenAlarmArmedAndSensorActivatedAndStatusInPending_AlarmShouldBeSetOff() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    //3,If pending alarm and all sensors are inactive, return to no alarm state.
    @Test
    void whenAlarmPendingAndSensorsInactive_statusShouldBeNoAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    //4,If alarm is active, change in sensor state should not affect the alarm state.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void whenAlarmActivated_andSensorStateChanged_alarmStateShouldNotChange(boolean status) {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor);

        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    //5,If a sensor is activated while already active and the system is in pending state, change it to alarm state.
    @Test
    void whenSensorActivatedWhileActive_andSystemInPending_stateShouldBeAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    //6,If a sensor is deactivated while already inactive, make no changes to the alarm state.
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"NO_ALARM", "PENDING_ALARM", "ALARM"})
    void whenSensorDeactivatedWhileInactive_stateShouldBeNoChange(AlarmStatus status) {
        when(securityRepository.getAlarmStatus()).thenReturn(status);
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor, false);

        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
    }

    //7,If the camera image contains a cat while the system is armed-home, put the system into alarm status.
    @Test
    void whenCatDetectedWhileAlarmed_statusShouldBeAlarm() {
        BufferedImage catImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), ArgumentMatchers.anyFloat())).thenReturn(true);
        securityService.processImage(catImage);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
    }

    //8,If the camera image does not contain a cat, change the status to no alarm as long as the sensors are not active.
    @Test
    void whenNoCatDetected_asLongAsSensorsNotActive_statusShouldBeNoAlarm() {
        Set<Sensor> sensors = allSensorsAsASet(3, false);
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(imageService.imageContainsCat(any(), ArgumentMatchers.anyFloat())).thenReturn(false);
        securityService.processImage(mock(BufferedImage.class));

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    //9,If the system is disarmed, set the status to no alarm.
    @Test
    void whenSystemDisArmed_statusShouldBeNoAlarm() {
        securityService.setArmingStatus((ArmingStatus.DISARMED));
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    //10,If the system is armed, reset all sensors to inactive.
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void whenSystemArmed_sensorsShouldBeInactive(ArmingStatus armingStatus) {
        Set<Sensor> sensors = allSensorsAsASet(3, true);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getSensors()).thenReturn(sensors);
        securityService.setArmingStatus(armingStatus);

        securityService.getSensors().forEach(sensor -> assertFalse(sensor.getActive()));
    }

    //11,If the system is armed-home while the camera shows a cat, set the alarm status to alarm.
    @Test
    void whenArmedHome_whileCatDetected_statusShouldBeAlarm() {
        BufferedImage catImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        securityService.processImage(catImage);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);

    }

    // For 100% coverage in Class and Method of SecurityService:

    @Test
    void addStatusListener() {
        securityService.addStatusListener(statusListener);
    }

    @Test
    void removeStatusListener() {
        securityService.removeStatusListener(statusListener);
    }

    @Test
    void addSensor() {
        securityService.addSensor(sensor);
    }

    @Test
    void removeSensor() {
        securityService.removeSensor(sensor);
    }

    // Test `else if` in changeSensorActivationStatus():
    @Test
    void whenAlarmWhileSensorDeactivated_AlarmStatusShouldBePending() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor);

        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

}
