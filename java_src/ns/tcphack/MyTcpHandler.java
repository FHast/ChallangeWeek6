package ns.tcphack;

import java.util.ArrayList;

class MyTcpHandler extends TcpHandler {

	private int[] myIP = new int[] { 0x20, 0x01, 0x06, 0x7c, 0x25, 0x64, 0xa1, 0x54, 0x9d, 0xe9, 0xef, 0x3a, 0x75, 0x6b,
			0xdc, 0xeb };
	private int[] serverIP = new int[] { 0x20, 0x01, 0x06, 0x7c, 0x25, 0x64, 0xa1, 0x70, 0x02, 0x04, 0x23, 0xff, 0xfe,
			0xde, 0x4b, 0x2c };
	private int serverPort = 7710;

	private int ack = 0;

	public static void main(String[] args) {
		new MyTcpHandler();
	}

	public MyTcpHandler() {
		super();

		int[] synpkt = getPacket(0, 0, 0);
		// set syn flag
		synpkt[53] = 0b00000010;

		boolean done = false;

		send(synpkt);

		while (!done) {
			// check for reception of a packet, but wait at most 500 ms:
			int[] rxpkt = this.receiveData(500);
			if (rxpkt.length == 0) {
				// nothing has been received yet
				System.out.println("Nothing...");
				continue;
			}

			// something has been received
			int len = rxpkt.length;

			// print the received bytes:
			System.out.println("Received " + len + " bytes: ");
			for (int i = 0; i < len; i++)
				System.out.println(i + " | " + Integer.toBinaryString(rxpkt[i]));
			System.out.println("");

			int seq = (int) (rxpkt[44] * Math.pow(2, 24) + rxpkt[45] * Math.pow(2, 16) + rxpkt[46] * Math.pow(2, 8)
					+ rxpkt[47]);

			System.out.println("Sequence number: " + seq);
			ack = seq + (len - 64) + 1;

			System.out.println("flags: " + rxpkt[53]);
			if (((byte) (rxpkt[53]) & 0b00000010) == 0b00000010) {
				// SYN packet from server --> send get
				ArrayList<Integer> getpktdata = getData("GET /s1859994 \n \n");
				int[] getpkt = getPacket(1, 1, getpktdata.size());
				for (int i = 0; i < getpktdata.size(); i++) {
					getpkt[60 + i] = getpktdata.get(i);
				}
				send(getpkt);
			} else if (((byte) (rxpkt[53]) & 0b00000001) != 0b00000001) {
				// kein FIN
				int[] ackpkt = getPacket(1, ack, 0);
				send(ackpkt);
			} else {
				// FIN
				int[] finpkt = getPacket(1, ack, 0);
				finpkt[53] = 0b00010001;
				send(finpkt);
			}
		}
	}

	private int[] getPacket(long seq, long ack, int databytes) {
		String sequence = Long.toBinaryString(seq);
		String acknowledge = Long.toBinaryString(ack);

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
		txpkt[5] = 20;
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
		txpkt[43] = 0b00011110;
		// sequence number
		if (sequence.length() <= 8) {
			txpkt[44] = Integer.parseInt(sequence.substring(0, sequence.length()), 2);
		} else if (sequence.length() <= 16) {
			txpkt[44] = Integer.parseInt(sequence.substring(0, 8), 2);
			txpkt[45] = Integer.parseInt(sequence.substring(8, sequence.length()), 2);
		} else if (sequence.length() <= 24) {
			txpkt[44] = Integer.parseInt(sequence.substring(0, 8), 2);
			txpkt[45] = Integer.parseInt(sequence.substring(8, 16), 2);
			txpkt[46] = Integer.parseInt(sequence.substring(16, sequence.length()), 2);
		} else {
			txpkt[44] = Integer.parseInt(sequence.substring(0, 8), 2);
			txpkt[45] = Integer.parseInt(sequence.substring(8, 16), 2);
			txpkt[46] = Integer.parseInt(sequence.substring(16, 24), 2);
			txpkt[47] = Integer.parseInt(sequence.substring(24, sequence.length()), 2);
		}
		// acknowledgement number
		if (acknowledge.length() <= 8) {
			txpkt[48] = Integer.parseInt(acknowledge.substring(0, acknowledge.length()), 2);
		} else if (acknowledge.length() <= 16) {
			txpkt[48] = Integer.parseInt(acknowledge.substring(0, 8), 2);
			txpkt[49] = Integer.parseInt(acknowledge.substring(8, acknowledge.length()), 2);
		} else if (acknowledge.length() <= 24) {
			txpkt[48] = Integer.parseInt(acknowledge.substring(0, 8), 2);
			txpkt[49] = Integer.parseInt(acknowledge.substring(8, 16), 2);
			txpkt[50] = Integer.parseInt(acknowledge.substring(16, acknowledge.length()), 2);
		} else {
			txpkt[48] = Integer.parseInt(acknowledge.substring(0, 8), 2);
			txpkt[49] = Integer.parseInt(acknowledge.substring(8, 16), 2);
			txpkt[50] = Integer.parseInt(acknowledge.substring(16, 24), 2);
			txpkt[51] = Integer.parseInt(acknowledge.substring(24, acknowledge.length()), 2);
		}
		// header length upper nibble
		txpkt[52] = 0x50;
		// code bits / flags
		if (ack != 0) {
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
		System.out.println("Sending " + pkt.length + " bytes: ");
		for (int i = 0; i < pkt.length; i++)
			System.out.println(i + " | " + Integer.toBinaryString(pkt[i]) + " ");
		System.out.println("");
		this.sendData(pkt); // send the packet
	}
}
