package server;

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
import java.util.Random;
import java.util.Enumeration;
import java.net.Socket;

public class FTPServer extends Thread {

	public FTPServer(Socket client, String path) throws Throwable {
		socket = client;
		file_path = path;
		if (file_path.endsWith("/"))
			file_path = file_path.substring(0, file_path.length() - 1);
		work_path = "/";
		socket_in = socket.getInputStream();
		socket_out = socket.getOutputStream();
		file_socket = new Socket();
		passive_socket = new ServerSocket();
		buffer = new byte[1024];
		file_buffer = new byte[3072];
		passive_mode = false;
		connected = true;
		logged = false;
		local_port = 20;
		remote_port = -1;
		username = "";
		password = "";
		rename_file = "";
	}

	public String getSocketIn() throws Throwable {
		String s = "";
		while (true) {
			int count = socket_in.read(buffer);
			if (count < 0)
			{
				connected = false;
				break;
			}
			s += new String(buffer, 0, count);
			if (s.endsWith("\r\n"))
			{
				s = s.substring(0, s.length() - 2);
				break;
			}
		}
		return s;
	}

	public void putSocketOut(String s) throws Throwable {
		socket_out.write(s.getBytes());
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

	public void user(String[] cmd) throws Throwable {
		if (cmd.length > 1)
			username = cmd[1];
		else
			username = "";
		if (username.equals("anonymous"))
			putSocketOut("331 User anonymous okay, send your e-mail address as password.\r\n");
		else
			putSocketOut("331 User name okay, need password.\r\n");
	}

	public void pass(String[] cmd) throws Throwable {
		if (cmd.length > 1)
			password = cmd[1];
		else
			password = "";
		if (username.equals("anonymous")) {
			logged = true;
			putSocketOut("230 User logged in, proceed.\r\n");
		}
		else
			putSocketOut("530 Not logged in.\r\n");
	}

	public void port(String[] cmd) throws Throwable {
		if (cmd.length > 1) {
			String[] ips = cmd[1].split(",");
			if (ips.length == 6) {
				remote_port = Integer.parseInt(ips[4]) * 256 + Integer.parseInt(ips[5]);
				if (passive_mode) {
					if (!file_socket.isClosed())
						file_socket.close();
					if (!passive_socket.isClosed())
						passive_socket.close();
					passive_mode = false;
				}
				putSocketOut("200 Entering Active Mode.\r\n");
			}
			else
				putSocketOut("501 PORT invalid format.\r\n");
		}
		else
			putSocketOut("501 PORT invalid format.\r\n");
	}

	public void pasv() throws Throwable {
		Random random = new Random();
		while (true) {
			local_port = random.nextInt(40000) + 20000;
			if (portAvailable(local_port))
				break;
		}
		if (!file_socket.isClosed())
			file_socket.close();
		if (!passive_socket.isClosed())
			passive_socket.close();
		passive_socket = new ServerSocket(local_port);
		InetAddress ip = getIp();
		String[] ips = ip.getHostAddress().split("\\.");
		String s = "227 Entering Passive Mode (";
		for (int i = 0; i < ips.length; i++)
			s = s + ips[i] + ",";
		s = s + String.valueOf(local_port / 256) + "," + String.valueOf(local_port % 256) + ").\r\n";
		putSocketOut(s);
		file_socket = passive_socket.accept();
		passive_mode = true;
	}

	public void retr(String[] cmd) throws Throwable {
		if (cmd.length > 1) {
			String s = file_path + work_path + cmd[1];
			if (cmd[1].startsWith("/"))
				s = file_path + cmd[1];
			for (int i = 2; i < cmd.length; i++)
				s = s + " " + cmd[i];
			File file = new File(s);
			if (!file.exists()) {
				putSocketOut("550 File not exists.\r\n");
				return;
			}
			if (file.isDirectory()) {
				putSocketOut("550 File is a directory.\r\n");
				return;
			}
			FileInputStream in = new FileInputStream(file);
			putSocketOut("150 Opening data connection for file.\r\n");
			if (passive_mode) {
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
				passive_socket.close();
				putSocketOut("226 Transfer complete.\r\n");
			}
			else {
				file_socket = new Socket(socket.getInetAddress(), remote_port);
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
				putSocketOut("226 Transfer complete.\r\n");
			}
		}
		else
			putSocketOut("501 RETR invalid format.\r\n");	
	}

	public void stor(String[] cmd) throws Throwable {
		if (cmd.length > 1) {
			String s = file_path + work_path + cmd[1];
			if (cmd[1].startsWith("/"))
				s = file_path + cmd[1];
			for (int i = 2; i < cmd.length; i++)
				s = s + " " + cmd[i];
			File file = new File(s);
			if (!file.exists())
				if (!file.createNewFile()) {
					putSocketOut("550 Permission denied.\r\n");
					return;
				}
			if (file.isDirectory()) {
				putSocketOut("550 File is a directory.\r\n");
				return;
			}
			FileOutputStream out = new FileOutputStream(file);
			putSocketOut("150 Opening data connection for file.\r\n");
			if (passive_mode) {
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
				passive_socket.close();
				putSocketOut("226 Transfer complete.\r\n");		
			}
			else {
				file_socket = new Socket(socket.getInetAddress(), remote_port);
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
				putSocketOut("226 Transfer complete.\r\n");
			}
		}
		else
			putSocketOut("501 STOR invalid format.\r\n");
	}

	public void dele(String[] cmd) throws Throwable {
		if (cmd.length > 1) {
			String s = file_path + work_path + cmd[1];
			if (cmd[1].startsWith("/"))
				s = file_path + cmd[1];
			for (int i = 2; i < cmd.length; i++)
				s = s + " " + cmd[i];
			File file = new File(s);
			if (!file.exists()) {
				putSocketOut("550 File not exists.\r\n");
				return;
			}
			if (file.isDirectory()) {
				putSocketOut("550 File is a directory.\r\n");
				return;
			}
			if (!file.delete()) {
				putSocketOut("550 Permission denied.\r\n");
				return;
			}
			putSocketOut("250 File delete successful.\r\n");
		}
		else
			putSocketOut("501 RMD invalid format.\r\n");
	}

	public void rnfr(String[] cmd) throws Throwable {
		if (cmd.length > 1) {
			rename_file = file_path + work_path + cmd[1];
			if (cmd[1].startsWith("/"))
				rename_file = file_path + cmd[1];
			for (int i = 2; i < cmd.length; i++)
				rename_file = rename_file + " " + cmd[i];
			File file = new File(rename_file);
			if (!file.exists()) {
				putSocketOut("550 File not exists.\r\n");
				return;
			}
			putSocketOut("350 File exists, ready for destination name.\r\n");
		}
		else
			putSocketOut("501 RNFR invalid format.\r\n");
	}

	public void rnto(String[] cmd) throws Throwable {
		if (rename_file.equals("")) {
			putSocketOut("503 Bad sequence of commands.\r\n");
			return;
		}
		if (cmd.length > 1) {
			String s = file_path + work_path + cmd[1];
			if (cmd[1].startsWith("/"))
				s = file_path + cmd[1];
			for (int i = 2; i < cmd.length; i++)
				s = s + " " + cmd[i];
			File file = new File(rename_file);
			if (file.renameTo(new File(s)))
				putSocketOut("250 File rename successful.\r\n");
			else
				putSocketOut("550 Permission denied.\r\n");
		}
		else
			putSocketOut("501 RNTO invalid format.\r\n");
	}

	public void list(String[] cmd) throws Throwable {
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			putSocketOut("502 FTPServer running on Windows and LIST is unusable.\r\n");
			return;
		}
		String s = "";
		if (cmd.length > 1 && !cmd[1].startsWith("-")) {
			if (cmd[1].startsWith("/"))
				s = file_path;
			else
				s = file_path + work_path;
			s = s + cmd[1];
			for (int i = 2; i < cmd.length; i++) {
				if (cmd[i].startsWith("-"))
					break;
				s = s + " " + cmd[i];
			}
		}
		else
			s = file_path + work_path;
		String[] cmd_ls = {"/bin/bash", "-c", "ls \"" + s + "\" -l"};
		InputStream in = Runtime.getRuntime().exec(cmd_ls).getInputStream();
		putSocketOut("150 Opening data connection for list.\r\n");
		if (passive_mode) {
			OutputStream out = file_socket.getOutputStream();
			while (true) {
				int count = in.read(file_buffer);
				if (count < 0) {
					break;
				}
				out.write(file_buffer, 0, count);
			}
			file_socket.close();
			passive_socket.close();
			putSocketOut("226 Transfer complete.\r\n");
		}
		else {
			file_socket = new Socket(socket.getInetAddress(), remote_port);
			OutputStream out = file_socket.getOutputStream();
			while (true) {
				int count = in.read(file_buffer);
				if (count < 0) {
					break;
				}
				for (int i = 0; i < count; i++) {
					if (file_buffer[i] == '\n')
						out.write('\r');
					out.write(file_buffer[i]);
				}
			}
			file_socket.close();
			putSocketOut("226 Transfer complete.\r\n");
		}
	}

	public void pwd() throws Throwable {
		putSocketOut("257 \"" + work_path + "\" is cwd.\r\n");
	}

	public void mkd(String[] cmd) throws Throwable {
		if (cmd.length > 1) {
			File file;
			String s = file_path + work_path + cmd[1];
			if (cmd[1].startsWith("/"))
				s = file_path + cmd[1];
			for (int i = 2; i < cmd.length; i++)
				s = s + " " + cmd[i];
			file = new File(s);
			if (file.exists()) {
				putSocketOut("550 File already exists.\r\n");
				return;
			}
			if (!file.mkdirs()) {
				putSocketOut("550 Permission denied.\r\n");
				return;
			}
			putSocketOut("250 Create directory successful.\r\n");		
		}
		else
			putSocketOut("501 MKD invalid format.\r\n");
	}

	public boolean removeDir(File file) {
		if (file.isDirectory()) {
			String[] children = file.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = removeDir(new File(file, children[i]));
				if (!success)
					return false;
			}
		}
		return file.delete();
	}

	public void rmd(String[] cmd) throws Throwable {
		if (cmd.length > 1) {
			File file;
			String s = file_path + work_path + cmd[1];
			if (cmd[1].startsWith("/"))
				s = file_path + cmd[1];
			for (int i = 2; i < cmd.length; i++)
				s = s + " " + cmd[i];
			file = new File(s);
			if (!file.exists()) {
				putSocketOut("550 Directory not exists.\r\n");
				return;
			}
			if (!file.isDirectory()) {
				putSocketOut("550 File is not a directory.\r\n");
				return;
			}
			if (!removeDir(file)) {
				putSocketOut("550 Permission denied.\r\n");
				return;
			}
			putSocketOut("250 Directory remove successful.\r\n");
		}
		else
			putSocketOut("501 RMD invalid format.\r\n");
	}

	public void cwd(String[] cmd) throws Throwable {
		if (cmd.length > 1) {
			String s = cmd[1];
			for (int i = 2; i < cmd.length; i++)
				s = s + " " + cmd[i];
			if (s.matches("\\.\\./?")) {
				cdup();
				return;
			}
			if (s.startsWith("/")) {
				File file = new File(file_path + s);
				if (!file.exists() || !file.isDirectory()) {
					putSocketOut("550 No such directory.\r\n");
					return;
				}
				if (!s.endsWith("/"))
					s = s + "/";
				work_path = s;
				putSocketOut("250 \"" + work_path + "\" is new cwd.\r\n");
			}
			else {
				File file = new File(file_path + work_path + s);
				if (!file.exists() || !file.isDirectory()) {
					putSocketOut("550 No such directory.\r\n");
					return;
				}
				if (!s.endsWith("/"))
					s = s + "/";
				work_path = work_path + s;
				putSocketOut("250 \"" + work_path + "\" is new cwd.\r\n");
			}
		}
		else
			putSocketOut("501 CWD invalid format.\r\n");
	}

	public void cdup() throws Throwable {
		if (work_path.equals("/"))
			putSocketOut("550 Permission denied.\r\n");
		else {
			String[] s = work_path.split("/");
			work_path = s[0];
			for (int i = 1; i < (s.length - 1); i++)
				work_path = work_path + "/" + s[i];
			work_path = work_path + "/";
			putSocketOut("250 \"" + work_path + "\" is new cwd.\r\n");
		}
	}

	public void syst() throws Throwable {
		putSocketOut("215 UNIX Type: L8.\r\n");
	}

	public void type(String[] cmd) throws Throwable {
		if (cmd.length > 1)
			switch (cmd[1]) {
				case "I":
					putSocketOut("200 Type set to I.\r\n");
					break;
				default:
					putSocketOut("502 Command not implemented.\r\n");
				break;
			}
		else
			putSocketOut("501 Command invalid args.\r\n");
	}

	public void size(String[] cmd) throws Throwable {
		if (cmd.length > 1) {
			File file;
			String s = cmd[1];
			for (int i = 2; i < cmd.length; i++)
				s = s + " " + cmd[i];
			if (s.matches("\\.\\./?")) {
				cdup();
				return;
			}
			if (s.startsWith("/"))
				file = new File(file_path + s);
			else
				file = new File(file_path + work_path + s);
			if (!file.exists()) {
				putSocketOut("550 No such file.\r\n");
				return;
			}
			putSocketOut("213 " + file.length() + "\r\n");
		}
		else
			putSocketOut("501 SIZE invalid format.\r\n");
	}

	public void quit() throws Throwable {
		putSocketOut("221 Goodbye.\r\n");
		socket.close();
		logged = false;
		connected = false;
	}

	public void parseCommand() throws Throwable {
		String[] cmd = getSocketIn().split(" ");
		switch (cmd[0]) {
			case "USER":
			case "user":
				user(cmd);
				break;
			case "PASS":
			case "pass":
				pass(cmd);
				break;
			case "PORT":
			case "port":
				if (logged)
					port(cmd);
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "PASV":
			case "pasv":
				if (logged)
					pasv();
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "RETR":
			case "retr":
				if (logged)
					retr(cmd);
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "STOR":
			case "stor":
				if (logged)
					stor(cmd);
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "DELE":
			case "dele":
				if (logged)
					dele(cmd);
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "RNFR":
			case "rnfr":
				if (logged)
					rnfr(cmd);
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "RNTO":
			case "rnto":
				if (logged)
					rnto(cmd);
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "LIST":
			case "list":
				if (logged)
					list(cmd);
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "PWD":
			case "pwd":
				if (logged)
					pwd();
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "MKD":
			case "mkd":
				if (logged)
					mkd(cmd);
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "RMD":
			case "rmd":
				if (logged)
					rmd(cmd);
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "CWD":
			case "cwd":
				if (logged)
					cwd(cmd);
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "CDUP":
			case "cdup":
				if (logged)
					cdup();
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "SYST":
			case "syst":
				if (logged)
					syst();
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "TYPE":
			case "type":
				if (logged)
					type(cmd);
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "SIZE":
			case "size":
				if (logged)
					size(cmd);
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			case "QUIT":
			case "quit":
				if (logged)
					quit();
				else
					putSocketOut("530 Not logged in.\r\n");
				break;
			default:
				putSocketOut("502 Command not implemented.\r\n");
				break;
		}
	}

	public void run() {
		try {
			putSocketOut("220-Welcome!\r\n");
			putSocketOut("220 FTPServer ready.\r\n");
			while (connected)
				parseCommand();
		} catch (Throwable e) {
		}
	}

	public static void main(String[] args) throws Throwable {
		String path = "./";
		for (int i = 0; i < args.length; i++)
			if  (args[i].equals("-d")) {
				path = args[i + 1];
				for (int j = i + 2; j < args.length; j++)
					path = path + " " + args[j];
				if (path.matches("\"[\\s\\S]*\"") || path.matches("'[\\s\\S]*'"))
					path = path.substring(1, path.length() - 1);
				else
					path = path.split(" ")[0];
				break;
			}
		if (!new File(path).exists()) {
			System.out.println("Path not exists.");
			return;
		}
		ServerSocket server = new ServerSocket(21);
		System.out.println("FTPServer running on port 21.");
		Socket client;
		while (true) {
			client = server.accept();
			new FTPServer(client, path).start();
		}
	}

	private Socket socket, file_socket;
	private ServerSocket passive_socket;
	private InputStream socket_in;
	private OutputStream socket_out;
	private byte[] buffer, file_buffer;
	private String file_path, work_path, username, password, rename_file;
	private int local_port, remote_port;
	private boolean connected, logged, passive_mode;
}
