/**
 *  Copyright (C) 2002-2024   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.freecol.android;

import java.io.IOException;

import net.sf.freecol.common.io.FreeColXMLWriter;
import net.sf.freecol.common.networking.Connection;
import net.sf.freecol.common.networking.MessageHandler;
import net.sf.freecol.common.networking.ServerAPI;


/**
 * The human player's link to the in-process server, over a loopback
 * socket.  The same idea as the desktop client's UserServerAPI.
 */
public final class LocalServerAPI extends ServerAPI {

    private Connection connection = null;
    private String name, host;
    private int port = -1;
    private MessageHandler messageHandler = null;


    @Override
    public synchronized Connection connect(String name, String host, int port)
        throws IOException {
        this.name = name;
        this.host = host;
        this.port = port;
        Connection c = new Connection(host, port, name);
        attach(c);
        return c;
    }

    @Override
    public synchronized boolean disconnect() {
        if (connection != null) {
            connection.disconnect();
            connection = null;
        }
        return true;
    }

    @Override
    public synchronized Connection reconnect() throws IOException {
        Connection c = new Connection(host, port, name);
        attach(c);
        return c;
    }

    @Override
    public synchronized Connection getConnection() {
        return connection;
    }

    @Override
    public synchronized void setMessageHandler(MessageHandler mh) {
        super.setMessageHandler(mh);
        messageHandler = mh;
    }

    private void attach(Connection c) {
        c.setMessageHandler(messageHandler);
        c.setWriteScope(FreeColXMLWriter.WriteScope.toServer());
        c.startReceiving();
        connection = c;
    }
}
