package edu.buffalo.cse.cse486586.groupmessenger2;

import java.util.Comparator;

public class Message {
    public String messageContent;
    public String senderOfMessage;
    public String messageType;
    public int sequenceProposed;
    public int proposedSequencePort;
    public String agreedSequenceNumber;
    public MessageStatus status;
    public int messagePriority;
    public Message(String messageString) {
        String[] parts = messageString.split(":");
        messageType = parts[0];
        proposedSequencePort = Integer.parseInt(parts[1]);
        sequenceProposed = Integer.parseInt(parts[2]);
        messageContent = parts[3];
    }
    public void setProposedSequence(int highest, String proposerPort) {
        int seqAndPort = Integer.parseInt(String.valueOf(highest) + proposerPort);
        sequenceProposed = highest;
        proposedSequencePort = Integer.parseInt(proposerPort);
        messagePriority = seqAndPort;
    }
    public void setSender(String senderPort) {
        senderOfMessage = senderPort;
    }
    public void setStatus(MessageStatus status) {
        this.status = status;
    }
    public synchronized void compareAndUpdateSequence(Message newMessage) {
        int seqAndPort = Integer.parseInt(String.valueOf(newMessage.sequenceProposed) + newMessage.proposedSequencePort);
        if (this.sequenceProposed < newMessage.sequenceProposed) {
            this.sequenceProposed = newMessage.sequenceProposed;
            this.messagePriority = seqAndPort;
            this.proposedSequencePort = newMessage.proposedSequencePort;

        } else if (this.sequenceProposed == newMessage.sequenceProposed) {
            // Tie - Breaker... Use the smaller sequence number to break the ties
            if (this.proposedSequencePort > newMessage.proposedSequencePort) {
                this.proposedSequencePort = newMessage.proposedSequencePort;
                this.messagePriority = seqAndPort;
            }

        }
    }

}
enum MessageStatus  {PROPOSED,AGREED};

class MessageComparator implements Comparator<Message> {
    @Override
    public int compare(Message m1, Message m2) {
        /*
        Reference : https://stackoverflow.com/questions/2839137/how-to-use-comparator-in-java-to-sort
         */
//               Log.d("Compare " + lhs, rhs);
//                if (mapAgreed.get(lhs).equals("proposed") && mapAgreed.get(rhs).equals("proposed")) {
//                    if (proposedPriority.get(lhs) - proposedPriority.get(rhs) == 0) {
//                        return proposedPriorityPort.get(lhs) - proposedPriorityPort.get(rhs);
//                    } else return proposedPriority.get(lhs) - proposedPriority.get(rhs);
//                }
//                else if (mapAgreed.get(lhs).equals("proposed") && !mapAgreed.get(rhs).equals("agreed")) return -1;
//                else if (mapAgreed.get(lhs).equals("agreed") && !mapAgreed.get(rhs).equals("proposed")) return -1;
//                else {
//                    if (proposedPriority.get(lhs) - proposedPriority.get(rhs) == 0) {
//                        return proposedPriorityPort.get(lhs) - proposedPriorityPort.get(rhs);
//                    } else return proposedPriority.get(lhs) - proposedPriority.get(rhs);
//                }
//                if (proposedPriority.get(lhs) - proposedPriority.get(rhs) == 0) {
//                        return proposedPriorityPort.get(lhs) - proposedPriorityPort.get(rhs);
//                } else return proposedPriority.get(lhs) - proposedPriority.get(rhs);
        return m1.messagePriority - m2.messagePriority;
    }
}