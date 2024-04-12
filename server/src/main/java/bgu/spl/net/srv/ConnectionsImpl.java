package bgu.spl.net.srv;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {

    private final Map<Integer, ConnectionHandler<T>> handlers = new ConcurrentHashMap<>();

    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler) {
        handlers.put(connectionId, handler);
    }

    @Override
    public boolean send(int connectionId, T msg) {
        if (handlers.containsKey(connectionId)) {
            handlers.get(connectionId).send(msg);
            return true;
        }
        return false;
    }
    
    @Override
    public void disconnect(int connectionId) {
        try {
            handlers.get(connectionId).close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        handlers.remove(connectionId);
    }


    public String toString() {
        String result = "ConnectionsImpl contains " + handlers.size() + " connections:\n";
        for (Map.Entry<Integer, ConnectionHandler<T>> entry : handlers.entrySet()) {
            result += "Connection ID: " + entry.getKey() + "\n";
        }
        return result;
    }
}
