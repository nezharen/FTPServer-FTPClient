package client;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Random;
import java.util.Enumeration;

public class FTPClient {

	public FTPClient() throws Throwable {
		stdin = new BufferedReader(new InputStreamReader(System.in));
		buffer = new byte[1024];
		file_buffer = new byte[3072];
		connected = false;
		passive_mode = false;
		host = "";
		ftp_port = -1;
		username = "";
		password = "";
	}

	public String getSocketIn() throws Throwable {
		String s = "";
		while (true) {
			int count = socket_in.read(buffer);
			if (count < 0) {
				connected = false;
				System.out.println("421 Service not available, remote server has closed connection.");
				break;
			}
			s += new String(buffer, 0, count);
			if (s.matches("[\\s\\S]*\r\n\\d{3} [\\s\\S]+\r\n") || s.matches("\\d{3} [\\s\\S]+\r\n"))
				break;
		}
		return s;
	}

	public void putSocketOut(String s) throws Throwable {
		socket_out.write((s + "\r\n").getBytes());
	}

	public boolean portAvailable(int port) throws Throwable {
		try {
			ServerSocket server = new ServerSocket(port);
			server.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public InetAddress getIp() throws Throwable {
		InetAddress localip = null;
		InetAddress netip = null;
		try {
			Enumeration<NetworkInterface> netInterfaces = NetworkInterface.getNetworkInterfaces();
			InetAddress ip = null;
			boolean found = false;
			while (netInterfaces.hasMoreElements() && !found) {
				NetworkInterface ni = netInterfaces.nextElement();
				Enumeration<InetAddress> address = ni.getInetAddresses();
				while (address.hasMoreElements()) {
					ip = address.nextElement();
					if (!ip.isSiteLocalAddress() && !ip.isLoopbackAddress()	&& ip.getHostAddress().indexOf(":") == -1) {
						netip = ip;
						found = true;
						break;
					} 
					else if (ip.isSiteLocalAddress() && !ip.isLoopbackAddress() && ip.getHostAddress().indexOf(":") == -1) {
						localip = ip;
					}
				}
			}
		} catch (SocketException e) {
			System.out.println("Error while finding local ip.");
		}
		if (netip != null && !"".equals(netip.getHostAddress()))
			return netip;
		else
			return localip;
	}

	public void connect() throws Throwable {
		System.out.println("Connecting " + host + ":" + ftp_port);
		try {
			socket = new Socket(host, ftp_port);
			socket_in = socket.getInputStream();
			socket_out = socket.getOutputStream();
			String s = getSocketIn();
			System.out.print(s);
			if (!(s.startsWith("2"))) {
				socket.close();
				return;
			}
			connected = true;
			login();
		} catch (SocketException e) {
			System.out.println("Error while connecting.");
		}
	}

	public void login() throws Throwable {
		System.out.print("username:");
		username = stdin.readLine();
		putSocketOut("USER " + username);
		String s = getSocketIn();
		System.out.print(s);
		if (!(s.startsWith("3"))) {
			return;
		}
		System.out.print("password:");
		password = stdin.readLine();
		putSocketOut("PASS " + password);
		s = getSocketIn();
		System.out.print(s);
	}

	public void system() throws Throwable {
		putSocketOut("SYST");
		System.out.print(getSocketIn());
	}

	public void binary() throws Throwable {
		putSocketOut("TYPE I");
		System.out.print(getSocketIn());
	}

	public void ascii() throws Throwable {
		putSocketOut("TYPE A");
		System.out.print(getSocketIn());
	}

	public void passive() throws Throwable {
		if (passive_mode) {
			passive_mode = false;
			System.out.println("Passive mode off.");
		}
		else {
			passive_mode = true;
			System.out.println("Passive mode on.");
		}
	}

	public void get(String remote) throws Throwable {
		String[] s = remote.split("/");
		String local = s[s.length - 1];
		s = local.split("\\\\");
		local = s[s.length - 1];
		get(remote, local);
	}

	public void get(String remote, String local) throws Throwable {
		if (passive_mode) {
			putSocketOut("PASV");
			String s = getSocketIn();
			System.out.print(s);
			if (!(s.startsWith("2"))) {
				return;
			}
			remote_port = Integer.parseInt((s.split(","))[4]) * 256 + Integer.parseInt(((s.split(","))[5].split("\\)"))[0]);
			file_socket = new Socket(host, remote_port);
			putSocketOut("RETR " + remote);
			s = getSocketIn();
			System.out.print(s);
			if (!(s.startsWith("1"))) {
				file_socket.close();
				return;
			}
			File file = new File(local);
			if (!file.exists())
				if (!file.createNewFile()) {
					System.out.println("Create local file failed.");
					file_socket.close();
					System.out.println(getSocketIn());
					return;
				}
			if (file.isDirectory()) {
				System.out.println("Local file is a directory.\r\n");
				file_socket.close();
				System.out.println(getSocketIn());
				return;
			}
			FileOutputStream out = new FileOutputStream(file);
			InputStream in = file_socket.getInputStream();
			while (true) {
				int count = in.read(file_buffer);
				if (count < 0) {
					break;
				}
				out.write(file_buffer, 0, count);
			}
			out.close();
			file_socket.close();
			System.out.print(getSocketIn());
		}
		else {
			Random random = new Random();
			while (true) {
				local_port = random.nextInt(40000) + 20000;
				if (portAvailable(local_port))
					break;
			}
			ServerSocket me = new ServerSocket(local_port);
			InetAddress ip = getIp();
			String[] ips = ip.getHostAddress().split("\\.");
			String s = "PORT ";
			for (int i = 0; i < ips.length; i++)
				s = s + ips[i] + ",";
			s = s + String.valueOf(local_port / 256) + "," + String.valueOf(local_port % 256);
			putSocketOut(s);
			s = getSocketIn();
			System.out.print(s);
			if (!(s.startsWith("2"))) {
				me.close();
				return;
			}
			putSocketOut("RETR " + remote);
			s = getSocketIn();
			System.out.print(s);
			if (!(s.startsWith("1"))) {
				me.close();
				return;
			}
			file_socket = me.accept();
			File file = new File(local);
			if (!file.exists())
				if (!file.createNewFile()) {
					System.out.println("Create local file failed.");
					file_socket.close();
					me.close();
					System.out.println(getSocketIn());
					return;
				}
			if (file.isDirectory()) {
				System.out.println("Local file is a directory.\r\n");
				file_socket.close();
				me.close();
				System.out.println(getSocketIn());
				return;
			}
			FileOutputStream out = new FileOutputStream(file);
			InputStream in = file_socket.getInputStream();
			while (true) {
				int count = in.read(file_buffer);
				if (count < 0) {
					break;
				}
				out.write(file_buffer, 0, count);
			}
			out.close();
			file_socket.close();
			me.close();
			System.out.print(getSocketIn());
		}
	}

	public void put(String local) throws Throwable {
		String[] s = local.split("/");
		String remote = s[s.length - 1];
		s = remote.split("\\\\");
		remote = s[s.length - 1];
		put(local, remote);
	}

	public void put(String local, String remote) throws Throwable {
		File file = new File(local);
		if (!file.exists()) {
			System.out.println("Local file not exists.");
			return;
		}
		if (file.isDirectory()) {
			System.out.println("Local file is directory.");
			return;
		}
		FileInputStream in = new FileInputStream(file);
		if (passive_mode) {
			putSocketOut("PASV");
			String s = getSocketIn();
			System.out.print(s);
			if (!(s.startsWith("2"))) {
				in.close();
				return;
			}
			remote_port = Integer.parseInt((s.split(","))[4]) * 256 + Integer.parseInt(((s.split(","))[5].split("\\)"))[0]);
			file_socket = new Socket(host, remote_port);
			putSocketOut("STOR " + remote);
			s = getSocketIn();
			System.out.print(s);
			if (!(s.startsWith("1"))) {
				in.close();
				file_socket.close();
				return;
			}
			OutputStream out = file_socket.getOutputStream();
			while (true) {
				int count = in.read(file_buffer);
				if (count < 0) {
					break;
				}
				out.write(file_buffer, 0, count);
			}
			in.close();
			file_socket.close();
			System.out.print(getSocketIn());
		}
		else {
			Random random = new Random();
			while (true) {
				local_port = random.nextInt(40000) + 20000;
				if (portAvailable(local_port))
					break;
			}
			ServerSocket me = new ServerSocket(local_port);
			InetAddress ip = getIp();
			String[] ips = ip.getHostAddress().split("\\.");
			String s = "PORT ";
			for (int i = 0; i < ips.length; i++)
				s = s + ips[i] + ",";
			s = s + String.valueOf(local_port / 256) + "," + String.valueOf(local_port % 256);
			putSocketOut(s);
			s = getSocketIn();
			System.out.print(s);
			if (!(s.startsWith("2"))) {
				in.close();
				me.close();
				return;
			}
			putSocketOut("STOR " + remote);
			s = getSocketIn();
			System.out.print(s);
			if (!(s.startsWith("1"))) {
				in.close();
				me.close();
				return;
			}
			file_socket = me.accept();
			OutputStream out = file_socket.getOutputStream();
			while (true) {
				int count = in.read(file_buffer);
				if (count < 0) {
					break;
				}
				out.write(file_buffer, 0, count);
			}
			in.close();
			file_socket.close();
			me.close();
			System.out.print(getSocketIn());
		}
	}

	public void delete(String file) throws Throwable {
		putSocketOut("DELE " + file);
		System.out.print(getSocketIn());
	}

	public void rename(String from, String to) throws Throwable {
		putSocketOut("RNFR " + from);
		String s = getSocketIn();
		System.out.print(s);
		if (!s.startsWith("3"))
			return;
		putSocketOut("RNTO " + to);
		System.out.print(getSocketIn());
	}

	public void pwd() throws Throwable {
		putSocketOut("PWD");
		System.out.print(getSocketIn());
	}

	public void mkdir(String path) throws Throwable {
		putSocketOut("MKD " + path);
		System.out.print(getSocketIn());
	}

	public void rmdir(String path) throws Throwable {
		putSocketOut("RMD " + path);
		System.out.print(getSocketIn());
	}

	public void cd(String path) throws Throwable {
		putSocketOut("CWD " + path);
		System.out.print(getSocketIn());
	}

	public void cdup() throws Throwable {
		putSocketOut("CDUP");
		System.out.print(getSocketIn());
	}

	public void ls(String cmd) throws Throwable {
		if (passive_mode) {
			putSocketOut("PASV");
			String s = getSocketIn();
			System.out.print(s);
			if (!(s.startsWith("2"))) {
				return;
			}
			remote_port = Integer.parseInt((s.split(","))[4]) * 256 + Integer.parseInt(((s.split(","))[5].split("\\)"))[0]);
			file_socket = new Socket(host, remote_port);
			putSocketOut("LIST" + cmd);
			s = getSocketIn();
			System.out.print(s);
			if (!(s.startsWith("1"))) {
				file_socket.close();
				return;
			}
			InputStream in = file_socket.getInputStream();
			while (true) {
				int count = in.read(file_buffer);
				if (count < 0) {
					break;
				}
				System.out.write(file_buffer, 0, count);
			}
			file_socket.close();
			System.out.print(getSocketIn());
		}
		else {
			Random random = new Random();
			while (true) {
				local_port = random.nextInt(40000) + 20000;
				if (portAvailable(local_port))
					break;
			}
			ServerSocket me = new ServerSocket(local_port);
			InetAddress ip = getIp();
			String[] ips = ip.getHostAddress().split("\\.");
			String s = "PORT ";
			for (int i = 0; i < ips.length; i++)
				s = s + ips[i] + ",";
			s = s + String.valueOf(local_port / 256) + "," + String.valueOf(local_port % 256);
			putSocketOut(s);
			s = getSocketIn();
			System.out.print(s);
			if (!(s.startsWith("2"))) {
				me.close();
				return;
			}
			putSocketOut("LIST" + cmd);
			s = getSocketIn();
			System.out.print(s);
			if (!(s.startsWith("1"))) {
				me.close();
				return;
			}
			file_socket = me.accept();
			InputStream in = file_socket.getInputStream();
			while (true) {
				int count = in.read(file_buffer);
				if (count < 0) {
					break;
				}
				System.out.write(file_buffer, 0, count);
			}
			file_socket.close();
			me.close();
			System.out.print(getSocketIn());
		}
	}

	public void size(String path) throws Throwable {
		putSocketOut("SIZE " + path);
		System.out.print(getSocketIn());
	}

	public void close() throws Throwable {
		putSocketOut("QUIT");
		System.out.print(getSocketIn());
		socket.close();
		connected = false;
		host = "";
		ftp_port = -1;
		username = "";
		password = "";
	}

	public void parseCommand() throws Throwable {
		String[] cmd = stdin.readLine().split(" ");
		switch (cmd[0]) {
			case "open":
				if (connected)
					System.out.println("Already connected, use close first.");
				else
					try {
						if (cmd.length > 1) {
							host = InetAddress.getByName(cmd[1]).getHostAddress();
							if (cmd.length > 2)
								ftp_port = Integer.parseInt(cmd[2]);
							else
								ftp_port = 21;
							connect();
						}
						else
							System.out.println("usage: open host port.");
					} catch (UnknownHostException e) {
						System.out.println("Error while connecting.");
					}
				break;
			case "user":
				if (connected)
					login();
				else
					System.out.println("Not connected.");
				break;
			case "close":
				if (connected)
					close();
				else
					System.out.println("Not connected.");
				break;
			case "system":
				if (connected)
					system();
				else
					System.out.println("Not connected.");
				break;
			case "binary":
				if (connected)
					binary();
				else
					System.out.println("Not connected.");
				break;
			case "ascii":
				if (connected)
					ascii();
				else
					System.out.println("Not connected.");
				break;
			case "passive":
				if (connected)
					passive();
				else
					System.out.println("Not connected.");
				break;
			case "get":
				if (connected)
					if (cmd.length > 1) {
						for (int i = 2; i < cmd.length; i++)
							cmd[1] = cmd[1] + " " + cmd[i];
						if (cmd[1].matches("\"[\\s\\S]*\"") || cmd[1].matches("'[\\s\\S]*'"))
							cmd[1] = cmd[1].substring(1, cmd[1].length() - 1);
						else
							cmd[1] = cmd[1].split(" ")[0];
						get(cmd[1]);
					}
					else
						System.out.println("usage: get remote-file.");
				else
					System.out.println("Not connected.");
				break;
			case "put":
				if (connected)
					if (cmd.length > 1) {
						for (int i = 2; i < cmd.length; i++)
							cmd[1] = cmd[1] + " " + cmd[i];
						if (cmd[1].matches("\"[\\s\\S]*\"") || cmd[1].matches("'[\\s\\S]*'"))
							cmd[1] = cmd[1].substring(1, cmd[1].length() - 1);
						else
							cmd[1] = cmd[1].split(" ")[0];
						put(cmd[1]);
					}
					else
						System.out.println("usage: put local-file.");
				else
					System.out.println("Not connected.");
				break;
			case "delete":
				if (connected)
					if (cmd.length > 1) {
						for (int i = 2; i < cmd.length; i++)
							cmd[1] = cmd[1] + " " + cmd[i];
						if (cmd[1].matches("\"[\\s\\S]*\"") || cmd[1].matches("'[\\s\\S]*'"))
							cmd[1] = cmd[1].substring(1, cmd[1].length() - 1);
						else
							cmd[1] = cmd[1].split(" ")[0];
						delete(cmd[1]);
					}
					else
						System.out.println("usage: delete local-file.");
				else
					System.out.println("Not connected.");
				break;
			case "rename":
				if (connected)
					if (cmd.length > 2) {
						String[] s;
						for (int i = 2; i < cmd.length; i++)
							cmd[1] = cmd[1] + " " + cmd[i];
						try {
							if (cmd[1].startsWith("\"")) {
								s = cmd[1].split("\"");
								cmd[2] = s[1];
								cmd[1] = s[2];
								for (int i = 3; i < s.length; i++)
									cmd[1] = cmd[1] + "\"" + s[i];
								if (s.length > 3)
									cmd[1] = cmd[1] + "\"";
							}
							else
								if (cmd[1].startsWith("'")) {
									s = cmd[1].split("'");
									cmd[2] = s[1];
									cmd[1] = s[2];
									for (int i = 3; i < s.length; i++)
										cmd[1] = cmd[1] + "'" + s[i];
									if (s.length > 3)
										cmd[1] = cmd[1] + "'";
								}
								else {
									s = cmd[1].split(" ");
									cmd[2] = s[0];
									cmd[1] = s[1];
									for (int i = 2; i < s.length; i++)
										cmd[1] = cmd[1] + " " + s[i];
								}
						} catch (Throwable e) {
							System.out.println("usage: rename old-path new-path.");
							break;
						}
						cmd[1] = cmd[1].trim();
						if (cmd[1].matches("\"[\\s\\S]*\"") || cmd[1].matches("'[\\s\\S]*'"))
							cmd[1] = cmd[1].substring(1, cmd[1].length() - 1);
						else
							cmd[1] = cmd[1].split(" ")[0];
						rename(cmd[2], cmd[1]);
					}
					else
						System.out.println("usage: rename old-path new-path.");
				else
					System.out.println("Not connected.");
				break;
			case "pwd":
				if (connected)
					pwd();
				else
					System.out.println("Not connected.");
				break;
			case "mkdir":
				if (connected)
					if (cmd.length > 1) {
						for (int i = 2; i < cmd.length; i++)
							cmd[1] = cmd[1] + " " + cmd[i];
						if (cmd[1].matches("\"[\\s\\S]*\"") || cmd[1].matches("'[\\s\\S]*'"))
							cmd[1] = cmd[1].substring(1, cmd[1].length() - 1);
						else
							cmd[1] = cmd[1].split(" ")[0];
						mkdir(cmd[1]);
					}
					else
						System.out.println("usage: mkdir path.");
				else
					System.out.println("Not connected.");
				break;
			case "rmdir":
				if (connected)
					if (cmd.length > 1) {
						for (int i = 2; i < cmd.length; i++)
							cmd[1] = cmd[1] + " " + cmd[i];
						if (cmd[1].matches("\"[\\s\\S]*\"") || cmd[1].matches("'[\\s\\S]*'"))
							cmd[1] = cmd[1].substring(1, cmd[1].length() - 1);
						else
							cmd[1] = cmd[1].split(" ")[0];
						rmdir(cmd[1]);
					}
					else
						System.out.println("usage: rmdir path.");
				else
					System.out.println("Not connected.");
				break;
			case "cd":
				if (connected)
					if (cmd.length > 1) {
						for (int i = 2; i < cmd.length; i++)
							cmd[1] = cmd[1] + " " + cmd[i];
						if (cmd[1].matches("\"[\\s\\S]*\"") || cmd[1].matches("'[\\s\\S]*'"))
							cmd[1] = cmd[1].substring(1, cmd[1].length() - 1);
						else
							cmd[1] = cmd[1].split(" ")[0];
						cd(cmd[1]);
					}
					else
						System.out.println("usage: cd path.");
				else
					System.out.println("Not connected.");
				break;
			case "cdup":
				if (connected)
					cdup();
				else
					System.out.println("Not connected.");
				break;
			case "ls":
			case "dir":
				if (connected)
					if (cmd.length > 1) {
						for (int i = 2; i < cmd.length; i++)
							cmd[1] = cmd[1] + " " + cmd[i];
						if (cmd[1].matches("\"[\\s\\S]*\"") || cmd[1].matches("'[\\s\\S]*'"))
							cmd[1] = cmd[1].substring(1, cmd[1].length() - 1);
						else
							cmd[1] = cmd[1].split(" ")[0];
						cmd[1] = " " + cmd[1];
						ls(cmd[1]);
					}
					else
						ls("");
				else
					System.out.println("Not connected.");
				break;
			case "size":
				if (connected)
					if (cmd.length > 1) {
						for (int i = 2; i < cmd.length; i++)
							cmd[1] = cmd[1] + " " + cmd[i];
						if (cmd[1].matches("\"[\\s\\S]*\"") || cmd[1].matches("'[\\s\\S]*'"))
							cmd[1] = cmd[1].substring(1, cmd[1].length() - 1);
						else
							cmd[1] = cmd[1].split(" ")[0];
						size(cmd[1]);
					}
					else
						System.out.println("usage: cd path.");
				else
					System.out.println("Not connected.");
				break;
			case "bye":
			case "exit":
			case "quit":
				if (connected)
					close();
				System.exit(0);
				break;
			default:
				System.out.println("Invalid command.");
				break;
		}
	}

	public static void main(String[] args) throws Throwable {
		FTPClient ftp_client = new FTPClient();
		while (true)
		{
			System.out.print("FTPClient> ");
			ftp_client.parseCommand();
		}
	}

	private BufferedReader stdin;
	private Socket socket, file_socket;
	private InputStream socket_in;
	private OutputStream socket_out;
	private byte[] buffer, file_buffer;
	private String host, username, password;
	private int ftp_port, local_port, remote_port;
	private boolean connected, passive_mode;
}
