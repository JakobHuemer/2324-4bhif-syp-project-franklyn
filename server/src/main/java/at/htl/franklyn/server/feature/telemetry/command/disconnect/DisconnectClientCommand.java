package at.htl.franklyn.server.feature.telemetry.command.disconnect;

import at.htl.franklyn.server.feature.telemetry.command.CommandBase;
import at.htl.franklyn.server.feature.telemetry.command.CommandType;

public class DisconnectClientCommand extends CommandBase {
    public DisconnectClientCommand() {
        super(CommandType.DISCONNECT, null);
    }
}
