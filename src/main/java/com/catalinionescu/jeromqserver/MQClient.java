package com.catalinionescu.jeromqserver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import com.catalinionescu.jeromqserver.events.MQEvent;
import com.catalinionescu.jeromqserver.events.MQEventClientConnect;
import com.catalinionescu.jeromqserver.events.MQEventClientDisconnect;
import com.catalinionescu.jeromqserver.events.MQEventExecutor;
import com.catalinionescu.jeromqserver.events.MQEventListener;
import com.catalinionescu.jeromqserver.events.MQEventSendHeartbeat;
import com.catalinionescu.jeromqserver.events.MQListener;
import com.catalinionescu.jeromqserver.packets.MQPacket;
import com.catalinionescu.jeromqserver.packets.MQPacketHeartbeatRequest;
import com.catalinionescu.jeromqserver.packets.MQPacketHeartbeatResponse;
import com.catalinionescu.jeromqserver.util.SerializationUtils;

public class MQClient {
    private final String host;
    private final int port;
    private ClientTask clientTask;
    private Thread clientThread;
    private Integer missedHeartbeats = 0;

    final long HEARTBEAT_DELAY = 1000;
    final int INITIAL_MAX_MISSED_HEARTBEATS = 10;

    private final Queue<MQEvent> eventQueue = new ConcurrentLinkedQueue<>();
    private final Map<Class<? extends MQEvent>, List<MQEventExecutor>> listeners = new HashMap<>();
    private final Queue<MQPacket> sendQueue = new ConcurrentLinkedQueue<>();

    /**
     * Creates an MQ client that will connect to the server at specified host and port.
     * 
     * @param host Server host to connect to
     * @param port Server port to connect to
     */
    public MQClient(final String host, final int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Processes the event queue and calls registered event handlers for the queued event types.
     */
    public void callListeners() {
        while (!eventQueue.isEmpty()) {
            MQEvent event = eventQueue.remove();
            for (Class<? extends MQEvent> clazz : listeners.keySet()) {
                if (event.getClass().equals(clazz)) {
                    for (MQEventExecutor listener : listeners.get(clazz)) {
                        listener.execute(event);
                    }
                }
            }
        }
    }

    /**
     * Registers an event handler for specified event type.
     * 
     * @param clazz Event type
     * @param handler Event handler
     * @return True if the new handler was registered
     */
    /*
     * public boolean registerHandler(Class<? extends MQEvent> clazz, MQEventListener handler) {
     * if (!listeners.containsKey(clazz)) {
     * listeners.put(clazz, new LinkedList<MQEventListener>());
     * }
     * return listeners.get(clazz).add(handler);
     * }
     */

    /**
     * Registers all event handlers for specified class.
     * 
     * @param clazz Class that implements one or more event handlers.
     */
    public void registerHandlers(final MQListener clazz) {
        Method[] publicMethods = clazz.getClass().getMethods();
        Set<Method> methods = new HashSet<>(publicMethods.length);
        for (Method method : publicMethods) {
            methods.add(method);
        }
        for (Method method : clazz.getClass().getDeclaredMethods()) {
            methods.add(method);
        }
        for (final Method method : methods) {
            final MQEventListener eh = method.getAnnotation(MQEventListener.class);
            if (eh == null)
                continue;
            final Class<?> checkClass;
            if (method.getParameterTypes().length != 1 || !MQEvent.class.isAssignableFrom(checkClass = method.getParameterTypes()[0])) {
                continue;
            }
            final Class<? extends MQEvent> eventClass = checkClass.asSubclass(MQEvent.class);
            // System.out.println("Found handler for " + eventClass.getName());
            MQEventExecutor executor = new MQEventExecutor() {
                @Override
                public void execute(MQEvent event) {
                    if (!eventClass.isAssignableFrom(event.getClass())) {
                        return;
                    }
                    try {
                        method.invoke(clazz, event);
                    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            };
            if (!listeners.containsKey(eventClass)) {
                listeners.put(eventClass, new LinkedList<MQEventExecutor>());
            }
            listeners.get(eventClass).add(executor);
        }
    }

    /**
     * Removes all event handlers for given event type.
     * 
     * @param clazz Event type
     */
    public void clearHandlers(Class<? extends MQEvent> clazz) {
        listeners.remove(clazz);
    }

    /**
     * Removes all event handlers.
     */
    public void clearHandlers() {
        listeners.clear();
    }

    /**
     * Adds a new {@link MQEvent} to the event queue, to be dispatched by {@link #callListeners} from main thread.
     * 
     * @param event Event to be added.
     */
    protected void addEvent(MQEvent event) {
        eventQueue.add(event);
    }

    /**
     * Queues a packet for sending.
     * 
     * @param packet Packet to be queued for sending
     */
    public void sendPacket(MQPacket packet) {
        sendQueue.add(packet);
    }

    /**
     * Starts the client threads.
     */
    public void start() {
        clientTask = new ClientTask(this);
        clientThread = new Thread(clientTask);
        clientThread.start();
    }

    /**
     * Stops the client threads.
     */
    public void stop() {
        clientTask.terminate();
    }

    /**
     * Retrieves the number of missed heartbeats since last reconnect.
     * 
     * @return Number of missed heartbeats
     */
    public int getMissedHeartbeats() {
        synchronized (missedHeartbeats) {
            return missedHeartbeats;
        }
    }

    private class ClientTask implements Runnable {
        private volatile boolean running = true;
        private volatile int maxMissedHeartbeats = INITIAL_MAX_MISSED_HEARTBEATS;
        private final MQClient client;

        public ClientTask(MQClient client) {
            this.client = client;
        }

        public void terminate() {
            running = false;
        }

        private Socket getClientSocket(ZContext context) {
            Socket socket = context.createSocket(ZMQ.DEALER);
            socket.connect(String.format("tcp://%s:%d", host, port));
            socket.setLinger(0);

            return socket;
        }

        @SuppressWarnings("resource")
        @Override
        public void run() {
            ZContext context = new ZContext();
            Socket socket = getClientSocket(context);
            /**
             * Set to true if we missed a large number of heartbeats and we attempted at least 1 reconnect.
             */
            boolean reconnected = false;

            int request = 0;
            long currentTime = System.currentTimeMillis();
            long lastHeartbeatSent = currentTime + HEARTBEAT_DELAY;
            MQPacket packet = null;
            while (running) {
                // Receive any messages sent by provider
                byte[] msg = socket.recv(ZMQ.DONTWAIT);
                if (msg != null) {
                    try {
                        packet = (MQPacket) SerializationUtils.deserialize(msg);
                        if (packet instanceof MQPacketHeartbeatResponse) {
                            synchronized (missedHeartbeats) {
                                if (reconnected) {
                                    missedHeartbeats = 0;
                                } else if (missedHeartbeats > 0) {
                                    missedHeartbeats--;
                                }
                            }
                            if (reconnected) {
                                maxMissedHeartbeats = INITIAL_MAX_MISSED_HEARTBEATS;
                                addEvent(new MQEventClientConnect(client));
                                // System.out.println("Client: Reestablished communication with server.");
                                reconnected = false;
                            }
                        } else {
                            System.out.println("Client response: " + (packet != null ? packet.toString() : "null"));
                        }
                    } catch (ClassNotFoundException | IllegalArgumentException e) {
                        /*
                         * IllegalArgumentException is never thrown in this since we're making sure msg is not null.
                         * ClassNotFoundException is a programming bug since both client and server should use same packet API.
                         */
                        e.printStackTrace();
                    }
                }

                // Periodically send a heartbeat message
                currentTime = System.currentTimeMillis();
                if (currentTime > lastHeartbeatSent) {
                    lastHeartbeatSent = currentTime + HEARTBEAT_DELAY;
                    packet = new MQPacketHeartbeatRequest(request++);
                    socket.send(SerializationUtils.serialize(packet));
                    synchronized (missedHeartbeats) {
                        missedHeartbeats++;
                    }
                    addEvent(new MQEventSendHeartbeat(client));
                }
                // Check we're still connected and reconnect if needed
                int localMissedHeartbeats = 0;
                synchronized (missedHeartbeats) {
                    localMissedHeartbeats = missedHeartbeats;
                }
                if (localMissedHeartbeats > maxMissedHeartbeats) {
                    addEvent(new MQEventClientDisconnect(client));
                    // System.out.println(String.format("Client: Missed %d heartbeats with server. Reconnecting...", maxMissedHeartbeats));
                    context.destroySocket(socket);
                    socket = getClientSocket(context);
                    // Increase the max number of failures
                    maxMissedHeartbeats *= 2;
                    synchronized (missedHeartbeats) {
                        missedHeartbeats = 0;
                    }
                    reconnected = true;
                }

                /*
                 * Sends any queued packets.
                 * 
                 * TODO: If we want the packets to be sent after connection drops and is resumed, we must add a check here. For now we let MQ handle this
                 * internally.
                 */
                while (!sendQueue.isEmpty()) {
                    socket.send(SerializationUtils.serialize(sendQueue.remove()));
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // silence is golden
                    running = false;
                }
            }
            context.destroy();
            System.out.println("client_task terminated");
        }
    }
}
