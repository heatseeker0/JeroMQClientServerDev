package com.catalinionescu.jeromqserver;

import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.catalinionescu.jeromqserver.packets.MQPacket;
import com.catalinionescu.jeromqserver.packets.MQPacketHeartbeatRequest;
import com.catalinionescu.jeromqserver.packets.MQPacketHeartbeatResponse;

public class JeroMQClientServer {
    private final int TCP_PORT = 1337;

    private class MQHandlerHeartbeat extends MQPacketHandler {
        public MQHandlerHeartbeat(MQServer server) {
            super(server);
        }

        @Override
        public void handle(MQPacket request) {
            getServer().sendPacket(new MQPacketHeartbeatResponse(request.getPacketId()));
        }
    }

    private MQServer createServer() {
        MQServer server = new MQServer(TCP_PORT);
        server.registerHandler(MQPacketHeartbeatRequest.class, new MQHandlerHeartbeat(server));
        server.start();
        return server;
    }

    private class CommandInput implements Runnable {
        private Queue<String> cmdQueue = new ConcurrentLinkedQueue<>();
        private volatile boolean running = true;

        public void terminate() {
            running = false;
        }

        public String getCmd() {
            return cmdQueue.poll();
        }

        @Override
        public void run() {
            try (Scanner sc = new Scanner(System.in)) {
                String cmd;
                while (running) {
                    System.out.println("cmd> ");
                    cmd = sc.nextLine().toLowerCase().trim();
                    cmdQueue.add(cmd);
                    running = !cmd.equals("stop");
                }
            }
        }
    }

    public void start() {
        MQClient client = new MQClient("localhost", TCP_PORT);
        client.start();

        MQServer server = createServer();

        CommandInput cmdHandler = new CommandInput();
        Thread cmdHandlerThread = new Thread(cmdHandler);
        cmdHandlerThread.start();

        boolean running = true;
        String cmd = "";
        while (running) {
            server.runHandlers();
            cmd = cmdHandler.getCmd();
            if (cmd != null) {
                switch (cmd) {
                    case "stop":
                        running = false;
                        break;
                    case "haltserver":
                        System.out.println("Simulating communication failure. Stopping server...");
                        server.stop();
                        break;
                    case "startserver":
                        System.out.println("Starting server...");
                        server = createServer();
                    case "help":
                    default:
                        System.out.println("Available commands:");
                        System.out.println("");
                        System.out.println("help - Prints this help message");
                        System.out.println("stop - Stops the demo");
                        System.out.println("haltserver - Stops the server to simulate comm failure");
                        System.out.println("startserver - Start the server");
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        System.out.println("Terminating user input...");
        cmdHandler.terminate();

        System.out.println("Terminating clients...");
        client.stop();

        System.out.println("Terminating server...");
        server.stop();
    }
}
