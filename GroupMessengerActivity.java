package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StreamCorruptedException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final int SERVER_PORT = 10000;
    public Map<String, ArrayList<String>> waitingPorts = new HashMap<String, ArrayList<String>>();
    public Map<String, ArrayList<String>> receivedPorts = new HashMap<String, ArrayList<String>>();
    public List<String> alivePorts = new ArrayList<String>(Arrays.asList("11108", "11112", "11116", "11120", "11124"));
    public List<String> deadPorts = new ArrayList<String>();
    public String myPort;
    int sequenceNo = 0;
    private int fileSequenceNo = 0;
    private PriorityQueue<Message> pQueue = new PriorityQueue<Message>(150, new MessageComparator());
    public String failedPort = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        final TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));


        /*
         * Calculate the port number that this AVD listens on.
         * Reference : PA1 Code
         */
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        myPort = String.valueOf((Integer.parseInt(portStr) * 2));

        try {
            /*
             * Create a server socket as well as a thread (AsyncTask) that listens on the server
             * port.
             *
             * AsyncTask is a simplified thread construct that Android provides.
             * http://developer.android.com/reference/android/os/AsyncTask.html
             * Reference : PA1
             */
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }


        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */

        findViewById(R.id.button4).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        EditText chatBox = (EditText) findViewById(R.id.editText1);
                        String message = chatBox.getText().toString(); //Copy the content of the edit text box
                        chatBox.setText(""); //Reset the edit text box
                        tv.append("Me : " + message+"\n"); // Msg sent on the avd displayed as Me
                        sendMessage(message, myPort);

                    }
                }
        );
        
    }
    @Override
    protected void onDestroy() {
        // Does not work :: --> Send a message from the failed AVD that it has failed
        super.onDestroy();
        String myFailedPort = myPort;
        String failureMessage =  "F:" + myPort + ":" + sequenceNo + ":" + myFailedPort;
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, failureMessage, myPort);
    }

    public synchronized void checkAndDeliver() throws InterruptedException {
        /*
        * The following code first checks if there are any failed ports, and removes messages from the failed port
        * checks the head of the queue, if it is deliverable/has an agreed sequence number, delivers it, i.e.
        * sends to the Content Provider
        * References :
        * ISIS Algorithm
        * Links :
        * https://studylib.net/doc/7830646/isis-algorithm-for-total-ordering-of-messages
        *
        */

        if (failedPort != null) {
            for (Message message : pQueue) {
                if (message.senderOfMessage.equals(failedPort)) pQueue.remove(failedPort);
            }
        }
        refreshQueue();
        while (pQueue.peek() != null && pQueue.peek().status == MessageStatus.AGREED) {
            Log.d("Removing frm Sequence", String.valueOf(pQueue.peek().messagePriority) + "checking port and seq :" + pQueue.peek().sequenceProposed + "" + pQueue.peek().proposedSequencePort);
            String front = pQueue.poll().messageContent;
            deliver(front);
            refreshQueue();
        }
    }
    public synchronized void deliver(String front) {
        /*
        * The following code stores the key, value pair, i.e. the sequence number and the corresponding message into the Content Provider
        * and then increments the sequence number
        * References :
         * The following code creates a file in the AVD's internal storage and stores a file.
         * References :
         * https://developer.android.com/training/data-storage/files#WriteInternalStorage
         * http://developer.android.com/training/basics/data-storage/files.html
         * PA1 Code
         */
        ContentResolver contentResolver = getContentResolver();
        Uri providerUri = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
        ContentValues keyValueToInsert = new ContentValues();
        // inserting "key" and "value" where key - sequence no of the message, value - content of the message
        keyValueToInsert.put("key", fileSequenceNo);
        Log.d("AcceptedKeySuccess" + fileSequenceNo, front);
        fileSequenceNo++;
        keyValueToInsert.put("value", front);
        try {
            Uri newUri = getContentResolver().insert(
                    providerUri,
                    keyValueToInsert
            );
        } catch (Exception e) {
            Log.e("insert failed", e.toString());
        }
    }


    private Uri buildUri(String scheme, String authority) {
        /*
         * The following code builds the Uri for the ContentProvider by setting the authority
         * and scheme
         * Reference :
         * PA2 Part A Code #OnPTestClickListener.buildUri()
         */
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    public synchronized Message getMessageFromQueue(String messageContent) {
        /*
        * The following code retrieves message from the queue by using messageContent to identify
        * messages
         */
        for (Message message : pQueue) {
            if (message.messageContent.equals(messageContent)) {
                return message;
            }
        }
        return null;
    }

    public synchronized void sendMessage(String message, String myPort) {
        /*
        The following code sends the message (multicasts) to the 5 AVDS and increments the sequence
        number
         */
        int seq = sequenceNo + 1;
        Log.d("Sending message fm " + myPort, message);
        String newM = "M:" + myPort + ":" + seq + ":" + message;
        sequenceNo++;
        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, newM, myPort);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    public synchronized void refreshQueue() {
        /*
        * The following code is used to rearrange the priority queue/hold back queue on the
        * basis of the updated sequence number
         */
        List<Message> listOfMessages = new ArrayList<Message>();
        while (!pQueue.isEmpty()) listOfMessages.add(pQueue.remove());
        Collections.sort(listOfMessages, new MessageComparator());
        pQueue.addAll(listOfMessages);
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Socket clientConnection = null;
            DataInputStream dataInputStream = null;
            DataOutputStream dataOutputStream = null;
            String clientPort = null;
            try {
                while (true) {
                    /*
                     * The following code listens for incoming connections and accepts if a connection attempt is made
                     * and reads the received string data and sends it for OnProgressUpdate using publishProgress and
                     * then sends an acknowledgement back to the client
                     *
                     * References :
                     * 1. https://stackoverflow.com/questions/7384678/how-to-create-socket-connection-in-android
                     * 2. https://stackoverflow.com/questions/17440795/send-a-string-instead-of-byte-through-socket-in-java
                     * 3. https://developer.android.com/reference/android/os/AsyncTask
                     * 4. PA1 Code
                     */

                    clientConnection = serverSocket.accept();
                    clientConnection.setSoTimeout(3000);
                    dataInputStream = new DataInputStream(clientConnection.getInputStream());
                    String reply = "Reply";
                    String readString = dataInputStream.readUTF();
                    Log.d("Rec", readString);
                    Message newMessage = new Message(readString);
                    if (newMessage.messageType.equals("M")) {
                        reply = replyProposal(newMessage);
                        checkAndDeliver();
                    } else if (newMessage.messageType.equals("A")) {
                        reply = acceptSequence(newMessage);
                        checkAndDeliver();
                    }
                    else if (newMessage.messageType.equals("F")) {
                        reply = acceptFailure(newMessage);
                        checkAndDeliver();
                    }
                    dataOutputStream = new DataOutputStream(clientConnection.getOutputStream());
                    dataOutputStream.writeUTF(reply);
                    publishProgress(readString);
                }
            } catch (InterruptedException e) {
                Log.e("Interrupted Exception", e.toString());
            } catch (SocketTimeoutException e) {
                Log.e("Timeout", e.toString());
            } catch (StreamCorruptedException e) {
                Log.e("Stream Corrupted", e.toString());
            } catch (IOException e) {
                try {
                    checkAndDeliver();
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                Log.e("IO Exception", e.toString());
            } finally {
                try {
                    if (clientConnection != null) clientConnection.close();
                    if (dataInputStream != null) dataInputStream.close();
                    if (dataOutputStream != null) dataOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return null;
        }

        protected void onProgressUpdate(String... strings) {
            /*
             * The following code attempts the delivery step
             *
            */
            try {
                checkAndDeliver();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public synchronized String replyProposal(Message newMessage) {
            /*
            The following code creates a proposal reply to send to the AVD that sent the message
            It sets the sender of the port, the proposed sequence number (highest of what it has
            proposed so far..) and the status of the message as proposed and then adds the message
            into the priority/hold back queue, once done, it attempts the delivery step
            * ISIS Algorithm
            * Links :
            * https://studylib.net/doc/7830646/isis-algorithm-for-total-ordering-of-messages
            *
            */
            Log.d("Message ", newMessage.messageType);
            int highest = sequenceNo;
            sequenceNo++;
            List<String> list = new ArrayList<String>();
            newMessage.setSender(String.valueOf(newMessage.proposedSequencePort));
            newMessage.setProposedSequence(highest, myPort);
            newMessage.setStatus(MessageStatus.PROPOSED);
            Log.d("Message Sender", newMessage.senderOfMessage);
            if (!pQueue.contains(newMessage)) {
                pQueue.add(newMessage);
                try {
                    checkAndDeliver();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            refreshQueue();
            String proposal = "P:" + myPort + ":" + highest + ":" + newMessage.messageContent;
            Log.d("Sending Proposal", proposal);
            return proposal;
        }

        public synchronized String acceptSequence(Message newMessage) {
            /*
            * The following code accepts the received sequence number, updates the message with
            * the received sequence and the proposer, sets the message status as agreed and rearranges
            * the hold back queue on the basis of the updated sequence number,
            * Updates the sequence number if the agreed sequence number is greater than the
            * current sequence number
            * Additionally, also removes messages from the failed port
             *            * ISIS Algorithm
             * Links :
             * https://studylib.net/doc/7830646/isis-algorithm-for-total-ordering-of-messages
             *
             */

            int seqAndPort = Integer.parseInt(String.valueOf(newMessage.sequenceProposed) + newMessage.proposedSequencePort);
            Message messageInQueue = null;

            messageInQueue = getMessageFromQueue(newMessage.messageContent);

            if (messageInQueue != null) {
                pQueue.remove(messageInQueue);
                messageInQueue.sequenceProposed = newMessage.sequenceProposed;
                messageInQueue.proposedSequencePort = newMessage.proposedSequencePort;
                messageInQueue.messagePriority = seqAndPort;
                messageInQueue.status = MessageStatus.AGREED;
                pQueue.add(messageInQueue);
            }
            refreshQueue();

            if (newMessage.sequenceProposed > sequenceNo)
                sequenceNo = newMessage.sequenceProposed;
            Log.d("Acceptance " + seqAndPort, newMessage.messageContent);
            int count = 0;
            for (Message msg : pQueue) {
                if (failedPort != null) {
                    Log.d("Failed port id:", failedPort);
                    if (msg.senderOfMessage.equals(failedPort)){ Log.d("Removing", msg.senderOfMessage); pQueue.remove(msg); }
                } else {
                    Log.d("Failed port not id","NA");
                }
            }
            refreshQueue();
            Log.d("Queue --->", String.valueOf(count));
            for (Message msg : pQueue) {
                Log.d(msg.senderOfMessage, String.valueOf(msg.messageContent)+":SQ:"+msg.messagePriority+":STATUS:"+msg.status);
            }
            Log.d("Queue ends", String.valueOf(count));
            String acceptanceMessage = "K:" + newMessage.proposedSequencePort + ":" + newMessage.sequenceProposed + ":" + newMessage.messageContent;
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                checkAndDeliver();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return acceptanceMessage;
        }
        public synchronized String acceptFailure(Message newMessage) {
            /*
             * The following code accepts a failure of the node, updates the current failed port,
             * it then removes all the messages from the failed AVD/Port
             */

            String recFailedPort = newMessage.messageContent;
            if (failedPort == null) {
                failedPort = recFailedPort;
                for (Message message : pQueue) {
                    Log.d("In failureQ", message.senderOfMessage);
                 if (message.senderOfMessage.equals(failedPort)) pQueue.remove(message);
                }
                refreshQueue();
            }
            Log.d("Replying to failure","...");
            String reply = "RF:" + newMessage.proposedSequencePort + ":" + newMessage.sequenceProposed + ":" + newMessage.messageContent;
            return reply;
        }




    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

            @Override
            protected synchronized Void doInBackground(String... msgs) {

                String curPort = null;
                String msgToSend = msgs[0];
//             * TODO: Fill in your client code that sends out a message.
//             *
//             *
//             * The following code first accepts proposals from each of the AVDS and update
//             * the sequence number of the message depending upon what is received
//             * and sends the updated sequence number
//             * sends the highest agreed sequence number to all the AVDS
//             *
//             * Reference :
//             * 1. https://stackoverflow.com/questions/17440795/send-a-string-instead-of-byte-through-socket-in-java
//             * 2. PA1 Code
                String messageContentRecevied = null;
                for (String port : alivePorts) {
                    try {
                        messageContentRecevied = sendData(port, msgToSend, msgs[1]);
                    } catch (SocketTimeoutException e) {
                        Log.e("Timeout", e.toString());
                        handleFailure(port);
                    } catch (StreamCorruptedException e) {
                        Log.e("Stream Corrupted", e.toString());
                        handleFailure(port);
                    } catch (IOException e) {
                        Log.e("IO Exception", e.toString());
                        handleFailure(port);
                    }
                }
                if (!messageContentRecevied.equals("RF")) sendAgreedSequence(messageContentRecevied);
                return null;
            }

            public void sendAgreedSequence(String message) {
                /*
                * The following code sends the updated sequence number to all the AVDS
                * Reference :
                 * 1. https://stackoverflow.com/questions/17440795/send-a-string-instead-of-byte-through-socket-in-java
                 * 2. PA1 Code
                 */
                Message messageInQueue = getMessageFromQueue(message);
                Log.d("Inside agreed ", messageInQueue.messageContent);
                if (messageInQueue != null) {
                    int highestP = messageInQueue.sequenceProposed + 1;
                    String acceptance = "A:" + messageInQueue.proposedSequencePort + ":" + highestP + ":" + messageInQueue.messageContent;
                    Log.d("Sending agreed seq " + messageInQueue.sequenceProposed, acceptance);
                    for (String port : alivePorts) {
                        try {
                            sendData(port, acceptance, myPort);
                        } catch (SocketTimeoutException e) {
                            Log.e("Timeout", e.toString());
                            handleFailure(port);
                        } catch (StreamCorruptedException e) {
                            Log.e("Stream Corrupted", e.toString());
                            handleFailure(port);
                        } catch (IOException e) {
                            Log.e("IO Exception", e.toString());
                            handleFailure(port);
                        }
                        try {
                            checkAndDeliver();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                    }
                }
            }

            public String sendData(String port, String messageToSend, String messageFrom) throws IOException {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(port));
                socket.setSoTimeout(3000);
                Log.d("Sending from:", messageFrom + "to" + port);
                DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
                dataOutputStream.writeUTF(messageToSend);
                DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());
                String receivedFromServer = dataInputStream.readUTF();
                Message newMessageFromServer = new Message(receivedFromServer);
                if (newMessageFromServer.messageType.equals("P")) {
                    updateMessage(newMessageFromServer);
                    try {
                        checkAndDeliver();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
                else if (newMessageFromServer.messageType.equals("K")) {
                    try {
                        checkAndDeliver();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                else if (newMessageFromServer.messageType.equals("RF")) {
                    return "RF";
                }
                Log.d("Sent from:", messageFrom + "to" + port);
                return newMessageFromServer.messageContent;
            }

            public synchronized void updateMessage(Message newMessage) {
                /*
                The following code is used to update the sequence number of the message
                 */
                Log.d("Proposal" + newMessage.sequenceProposed, String.valueOf(newMessage.proposedSequencePort));
                Message messageInQueue = getMessageFromQueue(newMessage.messageContent);
                if (messageInQueue != null) {
                    pQueue.remove(messageInQueue);
                    messageInQueue.compareAndUpdateSequence(newMessage);
                    pQueue.add(messageInQueue);
                }

                refreshQueue();
                if (newMessage.sequenceProposed > sequenceNo)
                    sequenceNo = newMessage.sequenceProposed;
            }

            public void handleFailure(String port) {
                /*
                * The following code -
                * if there is a failed node, Notify all the other AVDs by sending a failure
                * message
                 */
                try {
                    if (!deadPorts.contains(port)) {
                        failedPort = port;
                        deadPorts.add(port);
                        for (Message message : pQueue) {
                            if (message.senderOfMessage.equals(port)) message.status = MessageStatus.AGREED;
                        }
                        checkAndDeliver();
                        notifyFailureToAll(port);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            public synchronized void notifyFailureToAll(String failedPort) {
                /*
                * The following code sends message to all AVDs, notifying them about a failure
                 */
                String failureMessage =  "F:" + myPort + ":" + sequenceNo + ":" + failedPort;
                if (failedPort != null) {
                    for (Message message : pQueue) {
                        if (message.senderOfMessage.equals(failedPort)) pQueue.remove(failedPort);
                    }
                    refreshQueue();
                }
                for (String port : alivePorts) {
                    if (!port.equals(failedPort)) {
                            try {
                                sendData(port,failureMessage,myPort);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                }
            }
        }




















































