import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.zip.CRC32;

public class UDPServer {
    private static final int PACKET_SIZE = 300;
    private static final int WINDOW_SIZE = 4;

    private static DatagramSocket serverSocket;
    private static InetAddress clientAddress;
    private static int clientPort;
    private static int expectedSequenceNumber;
    private static int ackNumber;

    public static void main(String[] args) throws Exception {
        serverSocket = new DatagramSocket(9876);

        byte[] receiveData = new byte[PACKET_SIZE];

        expectedSequenceNumber = 0;
        ackNumber = 0;

        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, PACKET_SIZE);
            serverSocket.receive(receivePacket);

            clientAddress = receivePacket.getAddress();
            clientPort = receivePacket.getPort();

            byte[] packetData = new byte[receivePacket.getLength()];
            System.arraycopy(receivePacket.getData(), 0, packetData, 0, receivePacket.getLength());

            if (validateChecksum(packetData)) {
                int sequenceNumber = extractSequenceNumber(packetData);
                byte[] messageData = extractMessageData(packetData);

                if (sequenceNumber == expectedSequenceNumber) {
                    String message = new String(messageData);
                    if (message.equals("FIN")) {
                        System.out.println("Cliente " + clientAddress + ":" + clientPort +
                                " solicitou encerramento da conexão.");
                        break;
                    }

                    System.out.println("Mensagem recebida: " + message);

                    String ackMsg = "ACK:" + (ackNumber + 1);
                    byte[] ackData = addChecksum(ackMsg.getBytes());

                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
                    serverSocket.send(ackPacket);

                    expectedSequenceNumber++;
                    ackNumber++;

                    Thread.sleep(100);
                } else {
                    System.out.println("Pacote fora de ordem. Descartado.");

                    String ackMsg = "ACK:" + (ackNumber + 1);
                    byte[] ackData = addChecksum(ackMsg.getBytes());

                    DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, clientAddress, clientPort);
                    serverSocket.send(ackPacket);

                    Thread.sleep(100);
                }
            } else {
                System.out.println("Erro no pacote recebido. Descartado.");
            }
        }

        serverSocket.close();
    }

    private static byte[] addChecksum(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        long checksum = crc32.getValue();

        byte[] checksumBytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            checksumBytes[i] = (byte) (checksum >>> ((7 - i) * 8));
        }

        byte[] newData = new byte[data.length + 8];
        System.arraycopy(data, 0, newData, 0, data.length);
        System.arraycopy(checksumBytes, 0, newData, data.length, 8);

        return newData;
    }

    private static boolean validateChecksum(byte[] data) {
        byte[] checksumBytes = new byte[8];
        System.arraycopy(data, data.length - 8, checksumBytes, 0, 8);

        CRC32 crc32 = new CRC32();
        crc32.update(data, 0, data.length - 8);
        long checksum = crc32.getValue();

        long receivedChecksum = 0;
        for (int i = 0; i < 8; i++) {
            receivedChecksum |= (long) (checksumBytes[i] & 0xFF) << ((7 - i) * 8);
        }

        return checksum == receivedChecksum;
    }

    private static int extractSequenceNumber(byte[] data) {
        byte[] sequenceNumberBytes = new byte[4];
        System.arraycopy(data, 0, sequenceNumberBytes, 0, 4);

        int sequenceNumber = 0;
        for (int i = 0; i < 4; i++) {
            sequenceNumber |= (data[i] & 0xFF) << ((3 - i) * 8);
        }

        return sequenceNumber;
    }

    private static byte[] extractMessageData(byte[] data) {
        byte[] messageData = new byte[data.length - 8];
        System.arraycopy(data, 4, messageData, 0, data.length - 8);

        return messageData;
    }
}
