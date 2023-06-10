import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.zip.CRC32;

public class UDPClient {
    private static final int PACKET_SIZE = 300;
    private static final int TIMEOUT = 2000;      //timeout em milissegundos
    private static final int WINDOW_SIZE = 4;     //tamanho da janela de congestionamento

    private static DatagramSocket clientSocket;
    private static InetAddress serverAddress;
    private static int serverPort;
    private static int sequenceNumber;
    private static int ackNumber;

    public static void main(String[] args) throws Exception {

        if (args.length != 3) {
            System.out.println("Expected: java UDPClient <file name> <ip address> <server port>");
            return;
        }

        String fileName = args[0];
        serverAddress = InetAddress.getByName(args[1]);
        serverPort = Integer.parseInt(args[2]);

        clientSocket = new DatagramSocket();
        clientSocket.setSoTimeout(TIMEOUT);

        File file = new File(fileName);
        if (!file.exists()) {
            System.out.println("File not found.");
            return;
        }

        FileInputStream fis = new FileInputStream(file);
        byte[] fileData = new byte[(int) file.length()];
        fis.read(fileData);
        fis.close();

        int totalPackets = (int) Math.ceil((double) fileData.length / PACKET_SIZE);

        System.out.println("Starting file transfer " + fileName + " to " +
                serverAddress.getHostAddress() + ":" + serverPort);
        System.out.println("File size: " + fileData.length + " bytes");
        System.out.println("Total number of packets to be sent: " + totalPackets);

        long startTime = System.currentTimeMillis();

        sequenceNumber = 0;
        ackNumber = 0;

        int packetsSent = 0;
        int packetsReceived = 0;

        while (packetsReceived < totalPackets) {
            while (packetsSent - packetsReceived < WINDOW_SIZE && packetsSent < totalPackets) {
                int dataSize = Math.min(PACKET_SIZE, fileData.length - (packetsSent * PACKET_SIZE));
                byte[] sendData = new byte[PACKET_SIZE];
                System.arraycopy(fileData, packetsSent * PACKET_SIZE, sendData, 0, dataSize);

                sendData = addChecksum(sendData);

                DatagramPacket sendPacket = new DatagramPacket(sendData, dataSize, serverAddress, serverPort);
                clientSocket.send(sendPacket);

                packetsSent++;
                System.out.println("Packet sent " + packetsSent + "/" + totalPackets);

                Thread.sleep(100); //aguarda um curto período para visualizar a troca de mensagens
            }

            try {
                byte[] receiveData = new byte[PACKET_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, PACKET_SIZE);
                clientSocket.receive(receivePacket);

                if (validateChecksum(receiveData)) {
                    packetsReceived++;
                    System.out.println("ACK Received " + packetsReceived + "/" + totalPackets);
                } else {
                    System.out.println("Error in received packet. Discarded.");
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout. Resending packets...");
                packetsSent = packetsReceived;
            }
        }

        long endTime = System.currentTimeMillis();

        System.out.println("Transfer completed on " + (endTime - startTime) + " ms");
        System.out.println("File sent successfully.");

        clientSocket.close();
    }

    private static byte[] addChecksum(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        long checksum = crc32.getValue();

        byte[] checksumBytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            checksumBytes[i] = (byte) (checksum >>> (i * 8));
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
            receivedChecksum |= (long) (checksumBytes[i] & 0xFF) << (i * 8);
        }

        return checksum == receivedChecksum;
    }
}
