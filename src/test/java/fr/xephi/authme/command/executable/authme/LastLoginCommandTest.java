package fr.xephi.authme.command.executable.authme;

import fr.xephi.authme.cache.auth.PlayerAuth;
import fr.xephi.authme.command.CommandService;
import fr.xephi.authme.command.ExecutableCommand;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.output.MessageKey;
import org.bukkit.command.CommandSender;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Date;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test for {@link LastLoginCommand}.
 */
public class LastLoginCommandTest {

    private static final long HOUR_IN_MSEC = 3600 * 1000;
    private static final long DAY_IN_MSEC = 24 * HOUR_IN_MSEC;

    @Test
    public void shouldRejectNonExistentUser() {
        // given
        String player = "tester";
        DataSource dataSource = mock(DataSource.class);
        given(dataSource.getAuth(player)).willReturn(null);

        CommandService service = mock(CommandService.class);
        given(service.getDataSource()).willReturn(dataSource);

        CommandSender sender = mock(CommandSender.class);
        ExecutableCommand command = new LastLoginCommand();

        // when
        command.executeCommand(sender, Collections.singletonList(player), service);

        // then
        verify(dataSource).getAuth(player);
        verify(service).send(sender, MessageKey.USER_NOT_REGISTERED);
    }

    @Test
    public void shouldDisplayLastLoginOfUser() {
        // given
        String player = "SomePlayer";
        long lastLogin = System.currentTimeMillis() -
            (412 * DAY_IN_MSEC + 10 * HOUR_IN_MSEC - 9000);
        PlayerAuth auth = mock(PlayerAuth.class);
        given(auth.getLastLogin()).willReturn(lastLogin);
        given(auth.getIp()).willReturn("123.45.66.77");

        DataSource dataSource = mock(DataSource.class);
        given(dataSource.getAuth(player)).willReturn(auth);
        CommandService service = mock(CommandService.class);
        given(service.getDataSource()).willReturn(dataSource);

        CommandSender sender = mock(CommandSender.class);
        ExecutableCommand command = new LastLoginCommand();

        // when
        command.executeCommand(sender, Collections.singletonList(player), service);

        // then
        verify(dataSource).getAuth(player);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender, times(3)).sendMessage(captor.capture());
        String lastLoginString = new Date(lastLogin).toString();
        assertThat(captor.getAllValues().get(0),
            allOf(containsString(player), containsString(lastLoginString)));
        assertThat(captor.getAllValues().get(1), containsString("412 days 9 hours"));
        assertThat(captor.getAllValues().get(2), containsString("123.45.66.77"));
    }

    @Test
    public void shouldDisplayLastLoginOfCommandSender() {
        // given
        String name = "CommandSender";
        CommandSender sender = mock(CommandSender.class);
        given(sender.getName()).willReturn(name);

        long lastLogin = System.currentTimeMillis() -
            (412 * DAY_IN_MSEC + 10 * HOUR_IN_MSEC - 9000);
        PlayerAuth auth = mock(PlayerAuth.class);
        given(auth.getLastLogin()).willReturn(lastLogin);
        given(auth.getIp()).willReturn("123.45.66.77");

        DataSource dataSource = mock(DataSource.class);
        given(dataSource.getAuth(name)).willReturn(auth);
        CommandService service = mock(CommandService.class);
        given(service.getDataSource()).willReturn(dataSource);


        ExecutableCommand command = new LastLoginCommand();

        // when
        command.executeCommand(sender, Collections.<String>emptyList(), service);

        // then
        verify(dataSource).getAuth(name);
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender, times(3)).sendMessage(captor.capture());
        String lastLoginString = new Date(lastLogin).toString();
        assertThat(captor.getAllValues().get(0),
            allOf(containsString(name), containsString(lastLoginString)));
        assertThat(captor.getAllValues().get(1), containsString("412 days 9 hours"));
        assertThat(captor.getAllValues().get(2), containsString("123.45.66.77"));
    }

}
