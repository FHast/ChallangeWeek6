package ns.tcphack;

import java.nio.ByteBuffer;
import java.util.ArrayList;

class MyTcpHandler extends TcpHandler {

	private int[] myIP = new int[] { 0x20, 0x01, 0x06, 0x7c, 0x25, 0x64, 0xa1, 0x54, 0x9d, 0xe9, 0xef, 0x3a, 0x75, 0x6b,
			0xdc, 0xeb };
	private int[] serverIP = new int[] { 0x20, 0x01, 0x06, 0x7c, 0x25, 0x64, 0xa1, 0x70, 0x02, 0x04, 0x23, 0xff, 0xfe,
			0xde, 0x4b, 0x2c };
	private int serverPort = 7710;

	private long ack = -1;
	private long seq = 0;

	public static void main(String[] args) {
		new MyTcpHandler();
	}

	public MyTcpHandler() {
		super();

		// initial syn packet
		int[] synpkt = getPacket(0, 0, 0);
		// set syn flag
		synpkt[53] = 0b00000010;
		send(synpkt);
		seq++;

		boolean done = false;
		while (!done) {
			System.out.println();
			// check for reception of a packet, but wait at most 500 ms:
			int[] rxpkt = this.receiveData(500);
			if (rxpkt.length == 0) {
				// nothing has been received yet
				System.out.println("Nothing...");
				continue;
			} else {

				// something has been received
				int len = rxpkt.length;

				// print the received bytes:
				// System.out.println("Received " + len + " bytes: ");
				// for (int i = 0; i < len; i++)
				// System.out.println(i + " | " +
				// Integer.toBinaryString(rxpkt[i]));
				// System.out.println("");

				long pktseq = getSequenceNumber(rxpkt);
				long pktack = getAckNumber(rxpkt);

				// ack = ack % (int) (Math.pow(2, 32));
				// seq = seq % (int) (Math.pow(2, 32));

				System.out.println("Received " + len + " bytes: ");
				System.out.println("Sequence number: " + pktseq);
				System.out.println(rxpkt[44] + " / " + rxpkt[45] + " / " + rxpkt[46] + " / " + rxpkt[47]);
				System.out.println("Acknowledgement number: " + pktack);
				System.out.println(rxpkt[48] + " / " + rxpkt[49] + " / " + rxpkt[50] + " / " + rxpkt[51]);
				System.out.print("flags: " + rxpkt[53] + " = ");

				if (((byte) (rxpkt[53]) & 0b00000010) == 0b00000010) {
					System.out.println("SYN \n\n");

					// save server seq number / our next ack

					ack = pktseq + (len - 40 - (rxpkt[5]) + 1);

					// send ack

					int[] ackpkt = getPacket(seq, ack, 0);
					send(ackpkt);

					// SYN packet from server --> send get

					ArrayList<Integer> getpktdata = getData(
							"GET /s1859994 HTTP/1.1\r\nHost: 2001:67c:2564:a170:204:23ff:fede:4b2c \r\n\r\n");
					int[] getpkt = getPacket(seq, ack, getpktdata.size());
					for (int i = 0; i < getpktdata.size(); i++) {
						getpkt[60 + i] = getpktdata.get(i);
					}
					send(getpkt);
					seq += getpktdata.size();
				} else {

					System.out.println("\n\n" + pktseq + " / " + ack + "\n\n");

					// if (pktseq == ack) {

					System.out.println("-------------------here");

					ack = pktseq + (len - 40 - (rxpkt[5]));

					if (((byte) (rxpkt[53]) & 0b00000001) == 0b00000001 || rxpkt[53] == 17) {
						System.out.println("FIN");
						// FIN packet

						int[] finpkt = getPacket(seq, ack, 0);
						finpkt[53] = 0b00010001;
						send(finpkt);
						seq++;
						done = true;
					} else if (((byte) (rxpkt[53]) & 0b00001000) == 0b00001000 || rxpkt[53] == 16) {
						System.out.println("ACK");
						// Ack packet

						int[] ackpkt = getPacket(seq, ack, 0);
						send(ackpkt);
						seq++;
					}
					
					// }
				}
			}
		}
	}

	private int[] getPacket(long s, long a, int databytes) {
		int[] seq = new int[4];

		seq[0] = (byte) (s & 0xFF);
		seq[1] = (byte) ((s >> 8) & 0xFF);
		seq[2] = (byte) ((s >> 16) & 0xFF);
		seq[3] = (byte) ((s >> 24) & 0xFF);

		int[] ack = new int[4];

		ack[0] = (byte) (a & 0xFF);
		ack[1] = (byte) ((a >> 8) & 0xFF);
		ack[2] = (byte) ((a >> 16) & 0xFF);
		ack[3] = (byte) ((a >> 24) & 0xFF);

		// array of bytes in which we're going to build our packet:
		int[] txpkt = new int[60 + databytes];

		// IP HEADER [length = 40]

		// ip version in upper nibble
		txpkt[0] = 0x60;
		// Traffic class / flow label
		txpkt[1] = 0x00;
		txpkt[2] = 0x00;
		txpkt[3] = 0x00;
		// payload length
		txpkt[4] = 0x00;
		txpkt[5] = 20 + databytes;
		// next header
		txpkt[6] = 253;
		// hop limit
		txpkt[7] = 64;
		// source address
		for (int i = 0; i < myIP.length; i++) {
			txpkt[i + 8] = myIP[i];
		}
		// destination address
		for (int i = 0; i < myIP.length; i++) {
			txpkt[i + 24] = serverIP[i];
		}

		// TCP header [length = 20]

		// source port [1234]
		txpkt[40] = 0b00000100;
		txpkt[41] = 0b11010010;
		// dest port
		txpkt[42] = 0b00011110;
		txpkt[43] = 0b00011111;
		// sequence number
		txpkt[44] = seq[3];
		txpkt[45] = seq[2];
		txpkt[46] = seq[1];
		txpkt[47] = seq[0];
		// acknowledgement number
		txpkt[48] = ack[3];
		txpkt[49] = ack[2];
		txpkt[50] = ack[1];
		txpkt[51] = ack[0];
		// header length upper nibble
		txpkt[52] = 0x50;
		// code bits / flags
		if (a != 0) {
			txpkt[53] = 0b00010000;
		} else {
			txpkt[53] = 0b00000000;
		}
		// window size
		txpkt[54] = 0b00010000;
		txpkt[55] = 0;
		// Checksum
		txpkt[56] = 0;
		txpkt[57] = 0;
		// urgent pointer
		txpkt[58] = 0;
		txpkt[59] = 0;

		// [options] left out

		// data

		return txpkt;
	}

	private ArrayList<Integer> getData(String s) {
		ArrayList<Integer> res = new ArrayList<>();
		byte[] data = s.getBytes();
		for (byte b : data) {
			res.add((int) b);
		}
		return res;
	}

	private void send(int[] pkt) {
		System.out.println();
		System.out.println("<<<<< SEND >>>>>");
		// System.out.println("Sending " + pkt.length + " bytes: ");
		// for (int i = 0; i < pkt.length; i++)
		// System.out.println(i + " | " + Integer.toBinaryString(pkt[i]) + " ");
		// System.out.println("");
		System.out.println(pkt[44] + " / " + pkt[45] + " / " + pkt[46] + " / " + pkt[47]);
		System.out.println(pkt[48] + " / " + pkt[49] + " / " + pkt[50] + " / " + pkt[51]);
		System.out.println("flags: " + pkt[53]);
		System.out.println("---------------");
		System.out.println();
		this.sendData(pkt); // send the packet

	}

	private long getSequenceNumber(int[] pkt) {
		String s1 = "" + Integer.toBinaryString(pkt[44]);
		String s2 = "" + Integer.toBinaryString(pkt[45]);
		String s3 = "" + Integer.toBinaryString(pkt[46]);
		String s4 = "" + Integer.toBinaryString(pkt[47]);

		while (s1.length() < 8) {
			s1 = "0" + s1;
		}
		while (s2.length() < 8) {
			s2 = "0" + s2;
		}
		while (s3.length() < 8) {
			s3 = "0" + s3;
		}
		while (s4.length() < 8) {
			s4 = "0" + s4;
		}

		String sequence = "" + s1 + "" + s2 + "" + s3 + "" + s4;

		return Long.parseLong(sequence, 2);
	}

	private long getAckNumber(int[] pkt) {
		String s1 = "" + Integer.toBinaryString(pkt[48]);
		String s2 = "" + Integer.toBinaryString(pkt[49]);
		String s3 = "" + Integer.toBinaryString(pkt[50]);
		String s4 = "" + Integer.toBinaryString(pkt[51]);

		while (s1.length() < 8) {
			s1 = "0" + s1;
		}
		while (s2.length() < 8) {
			s2 = "0" + s2;
		}
		while (s3.length() < 8) {
			s3 = "0" + s3;
		}
		while (s4.length() < 8) {
			s4 = "0" + s4;
		}

		String acknowledge = "" + s1 + "" + s2 + "" + s3 + "" + s4;

		return Long.parseLong(acknowledge, 2);
	}
}
