package org.javacord.core.event.server;

import org.javacord.api.entity.server.Server;
import org.javacord.api.event.server.ServerMembersLoadedEvent;

/**
 * The implementation of {@link ServerMembersLoadedEvent}.
 */
public class ServerMembersLoadedEventImpl extends ServerEventImpl implements ServerMembersLoadedEvent {

    /**
     * Creates a new server members become available event.
     *
     * @param server The server of the event.
     */
    public ServerMembersLoadedEventImpl(Server server) {
        super(server);
    }

}
