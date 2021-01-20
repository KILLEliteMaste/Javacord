package org.javacord.api.listener.server;

import org.javacord.api.event.server.ServerMembersLoadedEvent;
import org.javacord.api.listener.GloballyAttachableListener;

/**
 * This listener listens to server members becoming available.
 */
@FunctionalInterface
public interface ServerMembersLoadedListener extends GloballyAttachableListener {

    /**
     * This method is called every time server members became available.
     *
     * @param event The event.
     */
    void onServerMembersLoaded(ServerMembersLoadedEvent event);
}
