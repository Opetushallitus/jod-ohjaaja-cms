/**
 * SPDX-FileCopyrightText: (c) 2000 Liferay, Inc. https://liferay.com
 * SPDX-License-Identifier: LGPL-2.1-or-later OR LicenseRef-Liferay-DXP-EULA-2.0.0-2023-06
 */

package com.liferay.arquillian.extension.junit.bridge.connector;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.osgi.framework.BundleContext;
import org.osgi.service.log.Logger;

/**
 * @author Matthew Tambara
 */
public class ArquillianConnectorThread extends Thread {

	public ArquillianConnectorThread(
			BundleContext bundleContext, InetAddress inetAddress, int port,
			String passcode, Logger logger)
		throws IOException {

		_bundleContext = bundleContext;
		_passcode = passcode;
		_logger = logger;
    _logger.info("Expected passcode: " + _passcode);
    System.out.println("Expected passcode: " + _passcode);

		setName("Arquillian-Connector-Thread");
		setDaemon(true);

		_serverSocket = new ServerSocket(port, 50, inetAddress);
    _logger.info("Arquillian-Connector-Thread listening on " + _serverSocket.getLocalSocketAddress());
	}

	public void close() throws IOException {
		interrupt();
    _logger.info("Arquillian-Connector-Thread closed");
		_serverSocket.close();
	}

	@Override
	public void run() {
    _logger.info("Arquillian-Connector-Thread started");
    System.out.println("Arquillian-Connector-Thread started");
		while (true) {
			try (Socket socket = _serverSocket.accept();
				ObjectOutputStream objectOutputStream = new ObjectOutputStream(
					socket.getOutputStream());
				ObjectInputStream objectInputStream = new ObjectInputStream(
					socket.getInputStream())) {

				String passcode = objectInputStream.readUTF();
        System.out.println("Arquillian-Connector-Thread received connection from " + socket.getRemoteSocketAddress());
        _logger.info("Arquillian-Connector-Thread received connection from " + socket.getRemoteSocketAddress());
        System.out.println("Passcode: " + _passcode);
        _logger.info("Passcode received: " + passcode);
        _logger.info("Expected passcode: " + _passcode);

				if ((_passcode != null) && !_passcode.equals(passcode)) {
					_logger.warn(
						"Pass code mismatch, dropped connection from {}",
						socket.getRemoteSocketAddress());

					continue;
				}

				while (true) {
					FrameworkCommand<?> frameworkCommand =
						(FrameworkCommand)objectInputStream.readObject();

					try {
						objectOutputStream.writeObject(
							new FrameworkResult<>(
								frameworkCommand.execute(_bundleContext)));
					}
					catch (Exception exception) {
						objectOutputStream.writeObject(
							new FrameworkResult<>(exception));
					}

					objectOutputStream.flush();
				}
			}
			catch (EOFException eofException) {
        eofException.printStackTrace();
			}
			catch (SocketException socketException) {
        socketException.printStackTrace();
				break;
			}
			catch (Exception exception) {
        exception.printStackTrace();
				_logger.error(
					"Dropped connection due to unrecoverable framework failure",
					exception);
			}
		}
	}

	private final BundleContext _bundleContext;
	private final Logger _logger;
	private final String _passcode;
	private final ServerSocket _serverSocket;

}
