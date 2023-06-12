import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.zip.CRC32;

public class Server {
    private static final int PORTA = 12345;
    private static final int TAMANHO_PACOTE = 300;
    private static final int TAMANHO_HEADER = 12;
    private static int numeroSequenciaEsperado;

    public static void main(String[] args) {
        try(FileOutputStream arquivoDestino = new FileOutputStream("arquivo_destino.txt");) {

            DatagramSocket socket = new DatagramSocket(PORTA);

            byte[] buffer = new byte[TAMANHO_PACOTE];
            DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);

            CRC32 crc32 = new CRC32();

            byte[] bufferConexao = new byte[1];
            DatagramPacket conexao = new DatagramPacket(bufferConexao, bufferConexao.length);

//            while () {
//                System.out.println("recebendo syn");
//
//                System.out.println("devolvendo syn ack");
//
//                System.out.println("recebendo ack");
//            }

            while (true) {
                System.out.println("comecando loop");
                socket.receive(pacote);

                InetAddress senderIp = pacote.getAddress();
                int senderPort = pacote.getPort();

                int numeroSequencia = lerNumeroDeSequencia(pacote.getData());
                System.out.println("pacote com o numero de sequencia: " + numeroSequencia);

                System.out.println("numero de sequencia recebido: " + numeroSequencia);
                System.out.println("numero de seuqncia esperado " + numeroSequenciaEsperado);

                if (Math.floor(Math.random() * 10) < 4) {
                    System.out.println("pacote numero " + numeroSequencia + " foi descartado");
                    continue;
                }


                if (numeroSequencia == numeroSequenciaEsperado) {
                    crc32.reset();
                    crc32.update(pacote.getData(), TAMANHO_HEADER, TAMANHO_PACOTE - TAMANHO_HEADER);

                    System.out.println("numero de crc cliente " + lerCRC(pacote.getData()));
                    System.out.println("numero de crc servidor " + crc32.getValue());
                    if (crc32.getValue() == lerCRC(pacote.getData())) {

                        arquivoDestino.write(pacote.getData(), TAMANHO_HEADER, TAMANHO_PACOTE - TAMANHO_HEADER);

                        numeroSequencia++;
                        numeroSequenciaEsperado = numeroSequencia;
                    }
                }
                System.out.println("enviando ack para o pacote com numero de sequencia: "+ numeroSequencia);
                enviarACK(socket, numeroSequencia, senderPort, senderIp);
                System.out.println();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void enviarACK(DatagramSocket socket, int numeroSequencia, int senderPort, InetAddress senderIp) throws IOException {
        byte[] ackData = new byte[] {
                (byte) ((numeroSequencia >>> 24) & 0xFF),
                (byte) ((numeroSequencia >>> 16) & 0xFF),
                (byte) ((numeroSequencia >>> 8) & 0xFF),
                (byte) (numeroSequencia & 0xFF)
        };
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length, senderIp, senderPort);
        socket.send(ackPacket);
    }

    private static long lerCRC(byte[] data) {
        return ((long) (data[4] & 0xFF) << 56) |
                ((long) (data[5] & 0xFF) << 48) |
                ((long) (data[6] & 0xFF) << 40) |
                ((long) (data[7] & 0xFF) << 32) |
                ((long) (data[8] & 0xFF) << 24) |
                ((long) (data[9] & 0xFF) << 16) |
                ((long) (data[10] & 0xFF) << 8) |
                ((long) (data[11] & 0xFF));
    }

    private static int lerNumeroDeSequencia(byte[] data) {
        return ((data[0] & 0xFF) << 24) |
                ((data[1] & 0xFF) << 16) |
                ((data[2] & 0xFF) << 8) |
                ((data[3] & 0xFF));
    }
}
