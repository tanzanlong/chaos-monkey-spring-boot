package de.codecentric.spring.boot.chaos.monkey.component;

import de.codecentric.spring.boot.chaos.monkey.configuration.AssaultProperties;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.mock;


@RunWith(PowerMockRunner.class)
@PrepareForTest(ScheduledTask.class)
public class ChaosMonkeySchedulerTest {
    @Mock
    private ScheduledTaskRegistrar registrar;
    @Mock
    private AssaultProperties config;
    @Mock
    private ChaosMonkeyRuntimeScope scope;

    @Test
    public void shouldTolerateMissingRegistry() {
        when(config.getRuntimeAssaultCronExpression()).thenReturn("*/5 * * * * ?");
        new ChaosMonkeyScheduler(null, config, scope);
        // no exception despite null injection
    }

    @Test
    public void shouldRespectTheOffSetting() {
        when(config.getRuntimeAssaultCronExpression()).thenReturn("OFF");

        new ChaosMonkeyScheduler(registrar, config, scope);
        verify(scope, never()).callChaosMonkey();
        verify(registrar, never()).scheduleCronTask(any());
    }

    @Test
    public void shouldScheduleATask() {
        String schedule = "*/1 * * * * ?";
        ScheduledTask scheduledTask = mock(ScheduledTask.class);
        when(config.getRuntimeAssaultCronExpression()).thenReturn(schedule);
        when(registrar.scheduleCronTask(any())).thenReturn(scheduledTask);

        new ChaosMonkeyScheduler(registrar, config, scope);

        verify(registrar).scheduleCronTask(argThat(hasScheduleLike(schedule)));
    }

    @Test
    public void shouldScheduleANewTaskAfterAnUpdate() {
        String schedule = "*/1 * * * * ?";
        ScheduledTask oldTask = mock(ScheduledTask.class);
        ScheduledTask newTask = mock(ScheduledTask.class);
        when(config.getRuntimeAssaultCronExpression()).thenReturn(schedule);
        when(registrar.scheduleCronTask(any())).thenReturn(oldTask, newTask);

        ChaosMonkeyScheduler cms = new ChaosMonkeyScheduler(registrar, config, scope);
        cms.reloadConfig();

        verify(registrar, times(2)).scheduleCronTask(argThat(hasScheduleLike(schedule)));
        verify(oldTask).cancel();
    }

    @Test
    public void shouldTriggerRuntimeScopeRunAttack() {
        String schedule = "*/1 * * * * ?";
        when(config.getRuntimeAssaultCronExpression()).thenReturn(schedule);
        when(registrar.scheduleCronTask(any())).thenAnswer(iom -> {
            iom.getArgumentAt(0, CronTask.class).getRunnable().run();
            return null;
        });

        new ChaosMonkeyScheduler(registrar, config, scope);
        verify(scope).callChaosMonkey();
    }

    private Matcher<CronTask> hasScheduleLike(String schedule) {
        return new BaseMatcher<CronTask>() {

            @Override
            public void describeTo(Description description) {
                description.appendText("has schedule like").appendText(schedule);
            }

            @Override
            public boolean matches(Object o) {
                return ((CronTask) o).getExpression().equals(schedule);
            }
        };
    }

    @Before
    public void initMocks() {
        MockitoAnnotations.initMocks(this);
    }
}
