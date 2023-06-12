import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.zip.CRC32;

public class Client {
    private static final String ENDERECO_SERVIDOR = "127.0.0.1";
    private static final int PORTA_SERVIDOR = 12345;
    private static final int TAMANHO_PACOTE = 300;
    private static final int SSTHRESHOLD = 16;
    private static final int TIMEOUT = 2000;
    private static final int TAMANHO_HEADER = 12;
    private static int ultimoAckRecebido;
    private static int tamanhoJanelaCongestionamento = 1;
    private static AlgoritmoCongestionamento algoritmoCongestionamento = AlgoritmoCongestionamento.SlowStart;

    public static void main(String[] args) {
        try {
            DatagramSocket socket = new DatagramSocket();

            InetAddress enderecoServidor = InetAddress.getByName(ENDERECO_SERVIDOR);
            FileInputStream arquivoOrigem = new FileInputStream("arquivo_origem.txt");

            byte[] buffer = new byte[TAMANHO_PACOTE];
            DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, enderecoServidor, PORTA_SERVIDOR);

            long startTime = System.currentTimeMillis();
            int ultimoACKConfirmado = -1;
            CRC32 crc32 = new CRC32();

            byte[] bufferConexao = new byte[1];
            DatagramPacket conexao = new DatagramPacket(bufferConexao, bufferConexao.length);

//            while () {
//                System.out.println("enviando syn");
//
//                System.out.println("recebendo syn ack");
//
//                System.out.println("enviando ack");
//            }

            while (true) {
                System.out.println("loop");
                if (ultimoAckRecebido <= tamanhoJanelaCongestionamento + ultimoAckRecebido) {
                    int bytesRead = arquivoOrigem.read(buffer, TAMANHO_HEADER, TAMANHO_PACOTE - TAMANHO_HEADER);
                    System.out.println("bytesRead: " + bytesRead);
                    //ultimo pacote
                    if (bytesRead == -1) {
                        System.out.println("ultimo pacote com numero: " + ultimoAckRecebido);
                        break;
                    }
                    System.out.println("enviando pacote com numero: " + ultimoAckRecebido);
                    enviarPacote(socket, buffer, pacote, ultimoAckRecebido, crc32);
                }

                if (System.currentTimeMillis() - startTime >= TIMEOUT) {
                    pacote.setLength(buffer.length);
                    enviarPacote(socket, buffer, pacote, ultimoAckRecebido, crc32);
                    startTime = System.currentTimeMillis();
                }

                try {
                    receberAck(socket);
                } catch (SocketTimeoutException e) {
                    System.out.println("package with number " + ultimoAckRecebido + " had TimeOut");
                    tamanhoJanelaCongestionamento = 1;
                    algoritmoCongestionamento = AlgoritmoCongestionamento.SlowStart;
                }

                System.out.println();
            }

            arquivoOrigem.close();
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void receberAck(DatagramSocket socket) throws SocketTimeoutException {
        if (esperarACK(socket)) {
            switch (algoritmoCongestionamento) {
                case SlowStart -> {
                    tamanhoJanelaCongestionamento *= 2;
                    System.out.println("aumentando a janela para: " + tamanhoJanelaCongestionamento);
                    if (tamanhoJanelaCongestionamento == SSTHRESHOLD) {
                        algoritmoCongestionamento = AlgoritmoCongestionamento.CongestionAvoidance;
                        System.out.println("mudando para o algoritmo Congestion Avoidance");
                    }
                }
                case CongestionAvoidance -> {
                    System.out.println("mudando para o algoritmo Congestion Avoidance");
                    tamanhoJanelaCongestionamento += 1;
                    System.out.println("aumentando a janela para: " + tamanhoJanelaCongestionamento);
                }
                case FastRetransmission -> {
                    System.out.println("mudando para o algoritmo Fast Retrasmission");
                    if (tamanhoJanelaCongestionamento > 1) {
                        tamanhoJanelaCongestionamento /= 2;
                        System.out.println("aumentando a janela para: " + tamanhoJanelaCongestionamento);
                    }
                }
            }
        }
    }

    private static void enviarPacote(DatagramSocket socket, byte[] buffer, DatagramPacket pacote, int numeroSequencia, CRC32 crc32) throws IOException {
        calcularEincluirNumeroDeSequencia(buffer, numeroSequencia);
        calcularEIncluirCRC(crc32, buffer, pacote.getLength());
        socket.send(pacote);
    }

    private static boolean esperarACK(DatagramSocket socket) throws SocketTimeoutException {
        System.out.println("esperando ack");
        byte[] ackData = new byte[4];
        DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);

        try {
            socket.setSoTimeout(TIMEOUT);
            socket.receive(ackPacket);
            int ackNumeroSequencia = lerNumeroDeSequencia(ackData);
            System.out.println(" ack recebido para pacote numero: " + ackNumeroSequencia);

            if (ehACKValido(ackNumeroSequencia)) {
                ultimoAckRecebido = ackNumeroSequencia;
                return true;
            }
        } catch (IOException e) {
            if (e instanceof SocketTimeoutException) {
                throw new SocketTimeoutException();
            }
            e.printStackTrace();
        }
        return false;
    }

    private static boolean ehACKValido(int ackNumeroSequencia) {
        return ackNumeroSequencia == ultimoAckRecebido + 1;
    }

    private static void calcularEIncluirCRC(CRC32 crc32, byte[] data, int length) {
        crc32.reset();
        crc32.update(data, TAMANHO_HEADER, TAMANHO_PACOTE - TAMANHO_HEADER);
        long crcValue = crc32.getValue();

        data[4] = (byte) ((crcValue >>> 56) & 0xFF);
        data[5] = (byte) ((crcValue >>> 48) & 0xFF);
        data[6] = (byte) ((crcValue >>> 40) & 0xFF);
        data[7] = (byte) ((crcValue >>> 32) & 0xFF);
        data[8] = (byte) ((crcValue >>> 24) & 0xFF);
        data[9] = (byte) ((crcValue >>> 16) & 0xFF);
        data[10] = (byte) ((crcValue >>> 8) & 0xFF);
        data[11] = (byte) (crcValue & 0xFF);
    }

    private static void calcularEincluirNumeroDeSequencia(byte[] data, int numeroDeSequencia) {
        data[0] = (byte) ((numeroDeSequencia >>> 24) & 0xFF);
        data[1] = (byte) ((numeroDeSequencia >>> 16) & 0xFF);
        data[2] = (byte) ((numeroDeSequencia >>> 8) & 0xFF);
        data[3] = (byte) (numeroDeSequencia & 0xFF);
    }

    private static int lerNumeroDeSequencia(byte[] data) {
        return ((data[0] & 0xFF) << 24) |
                ((data[1] & 0xFF) << 16) |
                ((data[2] & 0xFF) << 8) |
                ((data[3] & 0xFF));
    }

    private enum AlgoritmoCongestionamento {
        SlowStart,
        CongestionAvoidance,
        FastRetransmission
    }
}
