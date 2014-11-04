package com.catalinionescu.jeromqserver.unused;

import java.util.Random;

import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.PollItem;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

public class JeroMQServerProxy {
    // ---------------------------------------------------------------------
    // This is our client task
    // It connects to the server, and then sends a request once per second
    // It collects responses as they arrive, and it prints them out. We will
    // run several client tasks in parallel, each with a different random ID.

    private Random rand = new Random(System.nanoTime());

    private class client_task implements Runnable {
        private volatile boolean running = true;

        public void terminate() {
            running = false;
        }

        @SuppressWarnings("resource")
        @Override
        public void run() {
            ZContext ctx = new ZContext();
            Socket client = ctx.createSocket(ZMQ.DEALER);
            // Set random identity to make tracing easier
            String identity = String.format("%04X-%04X", rand.nextInt(), rand.nextInt());
            client.setIdentity(identity.getBytes());
            client.connect("tcp://localhost:5570");

            @SuppressWarnings("unused")
            PollItem[] items = new PollItem[] { new PollItem(client, Poller.POLLIN) };

            int requestNbr = 0;
            while (running) {
                if (requestNbr < 10) {
                    String msg = String.format("request #%d", ++requestNbr);
                    System.out.println("Client request: " + msg);
                    client.send(msg, 0);
                }

                ZMsg zmsg = ZMsg.recvMsg(client, ZMQ.DONTWAIT);
                if (zmsg != null) {
                    ZFrame content = zmsg.pop();
                    zmsg.destroy();
                    System.out.println("Client response: " + (content == null ? "null" : content.toString()));
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            client.close();
            ctx.destroy();
            System.out.println("client_task terminated");
        }
    }

    // This is our server task.
    // It uses the multithreaded server model to deal requests out to a pool
    // of workers and route replies back to clients. One worker can handle
    // one request at a time but one client can talk to multiple workers at
    // once.

    private class server_task implements Runnable {
        ZContext ctx = new ZContext();

        public ZContext getContext() {
            return ctx;
        }

        @Override
        public void run() {
            try (Socket frontend = ctx.createSocket(ZMQ.ROUTER);
                    Socket backend = ctx.createSocket(ZMQ.DEALER)) {

                // Frontend socket talks to clients over TCP
                frontend.bind("tcp://*:5570");

                // Backend socket talks to workers over inproc
                backend.bind("inproc://backend");

                // Connect backend to frontend via a proxy
                ZMQ.proxy(frontend, backend, null);
            }
            ctx.destroy();

            System.out.println("server_task terminated");
        }
    }

    // Each worker task works on one request at a time and sends a random number
    // of replies back, with random delays between replies:
    private class server_worker implements Runnable {
        private ZContext serverContext;

        private volatile boolean running = true;

        public void terminate() {
            running = false;
        }

        public server_worker(ZContext ctx) {
            this.serverContext = ctx;
        }

        @Override
        public void run() {
            try (Socket worker = serverContext.createSocket(ZMQ.DEALER)) {
                worker.connect("inproc://backend");

                while (running) {
                    try {
                        // The DEALER socket gives us the address envelope and message
                        ZMsg msg = ZMsg.recvMsg(worker, ZMQ.DONTWAIT);
                        if (msg != null) {
                            ZFrame address = msg.pop();
                            ZFrame content = msg.pop();
                            msg.destroy();

                            System.out.println("Server request: " + content.toString());
                            content.reset("Response");
                            address.send(worker, ZFrame.REUSE + ZFrame.MORE);
                            content.send(worker, ZFrame.REUSE);
                            address.destroy();
                            content.destroy();
                        }
                    } catch (Exception e) {
                        // Silence any shutdown warnings about already closed contexts and terminate
                        running = false;
                    }
                    if (running) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            // silence
                        }
                    }
                }
            }
            System.out.println("server_worker terminated");
        }
    }

    public void doWork() throws InterruptedException {
        @SuppressWarnings("resource")
        ZContext ctx = new ZContext();

        client_task[] clients = new client_task[1];
        for (int i = 0; i < clients.length; i++) {
            clients[i] = new client_task();
            new Thread(clients[i]).start();
        }

        server_task server = new server_task();
        new Thread(server).start();

        server_worker[] workers = new server_worker[5];

        // Launch pool of worker threads, precise number is not critical
        for (int threadNbr = 0; threadNbr < 5; threadNbr++) {
            workers[threadNbr] = new server_worker(server.getContext());
            new Thread(workers[threadNbr]).start();
        }

        // Run for 5 seconds then quit
        Thread.sleep(5 * 1000);

        for (int i = 0; i < clients.length; i++) {
            clients[i].terminate();
        }

        for (int i = 0; i < workers.length; i++) {
            workers[i].terminate();
        }

        ctx.destroy();
    }
}
