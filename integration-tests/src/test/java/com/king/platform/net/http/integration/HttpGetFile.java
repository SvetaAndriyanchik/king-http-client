// Copyright (C) king.com Ltd 2015
// https://github.com/king/king-http-client
// Author: Magnus Gustafsson
// License: Apache 2.0, https://raw.github.com/king/king-http-client/LICENSE-APACHE

package com.king.platform.net.http.integration;


import com.king.platform.net.http.ConfKeys;
import com.king.platform.net.http.FileResponseConsumer;
import com.king.platform.net.http.HttpClient;
import com.king.platform.net.http.HttpResponse;
import io.netty.util.ResourceLeakDetector;
import org.eclipse.jetty.server.HttpOutput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class HttpGetFile {



	IntegrationServer integrationServer;
	private HttpClient httpClient;
	private int port;
	private TemporaryFile temporaryFile;


	@BeforeEach
	public void setUp(@TempDir Path tempDir) throws Exception {
		temporaryFile = new TemporaryFile(tempDir);

		ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);

		integrationServer = new JettyIntegrationServer();
		integrationServer.start();
		port = integrationServer.getPort();

		httpClient = new TestingHttpClientFactory()
			.setOption(ConfKeys.TOTAL_REQUEST_TIMEOUT_MILLIS, 0)
			.setOption(ConfKeys.IDLE_TIMEOUT_MILLIS, 1000)
			.create();


		httpClient.start();

	}


	@Test
	public void get32MBFile() throws Exception {
		temporaryFile.generateContent(32 * 1024);

		integrationServer.addServlet(new FileServingHttpServlet(1024 * 8, temporaryFile), "/getFile");

		HttpResponse<byte[]> httpResponse = httpClient.createGet("http://localhost:" + port + "/getFile").build(MD5CalculatingResponseBodyConsumer::new).execute().get(20, TimeUnit.SECONDS);

		byte[] clientMd5 = httpResponse.getBody();


		assertEquals(hexStringFromBytes(temporaryFile.getFileMd5()), hexStringFromBytes(clientMd5));

	}

	@Test
	public void get64MBFile() throws Exception {

		temporaryFile.generateContent(64 * 1024);

		integrationServer.addServlet(new AsyncFileServingHttpServlet(temporaryFile), "/getFile");

		HttpResponse<byte[]> response = httpClient.createGet("http://localhost:" + port + "/getFile").build(MD5CalculatingResponseBodyConsumer::new).execute().get(20, TimeUnit.SECONDS);

		byte[] clientMd5 = response.getBody();

		assertEquals(hexStringFromBytes(temporaryFile.getFileMd5()), hexStringFromBytes(clientMd5));

	}



	@Test
	public void get64MBFileToLocalFile() throws Exception {

		temporaryFile.generateContent(64 * 1024);

		integrationServer.addServlet(new AsyncFileServingHttpServlet(temporaryFile), "/getFile");

		File tempFile = temporaryFile.getTempFile();

		CompletableFuture<HttpResponse<File>> execute = httpClient.createGet("http://localhost:" + port + "/getFile")
			.build(() -> new FileResponseConsumer(tempFile))
			.execute();

		HttpResponse<File> fileHttpResponse = execute.get(1000, TimeUnit.MILLISECONDS);

		File body = fileHttpResponse.getBody();

		assertEquals(64*1024*1024, body.length());

	}


	@Test
	public void get1KbFile() throws Exception {
		temporaryFile.generateContent(1);

		integrationServer.addServlet(new FileServingHttpServlet(1024 * 8, temporaryFile), "/getFile");


		HttpResponse<byte[]> response = httpClient.createGet("http://localhost:" + port + "/getFile").build(MD5CalculatingResponseBodyConsumer::new).execute().get(1, TimeUnit.SECONDS);


		byte[] clientMd5 = response.getBody();


		assertEquals(hexStringFromBytes(temporaryFile.getFileMd5()), hexStringFromBytes(clientMd5));

	}

	@Test
	public void get1KbFileTwice() throws Exception {
		temporaryFile.generateContent(1);

		integrationServer.addServlet(new FileServingHttpServlet(1024 * 8, temporaryFile), "/getFile");


		httpClient.createGet("http://localhost:" + port + "/getFile").build(MD5CalculatingResponseBodyConsumer::new).execute().get(1, TimeUnit.SECONDS);

		HttpResponse<byte[]> response = httpClient.createGet("http://localhost:" + port + "/getFile").build(MD5CalculatingResponseBodyConsumer::new).execute().get(1, TimeUnit.SECONDS);

		byte[] clientMd5 = response.getBody();


		assertEquals(hexStringFromBytes(temporaryFile.getFileMd5()), hexStringFromBytes(clientMd5));

	}

	@Test
	public void get16KbFileWithCallback() throws Exception {
		temporaryFile.generateContent(16);

		integrationServer.addServlet(new FileServingHttpServlet(1024, temporaryFile), "/getFile");

		HttpResponse<byte[]> response = httpClient.createGet("http://localhost:" + port + "/getFile").build(MD5CalculatingResponseBodyConsumer::new).execute().get(1, TimeUnit.SECONDS);

		byte[] clientMd5 = response.getBody();

		assertEquals(hexStringFromBytes(temporaryFile.getFileMd5()), hexStringFromBytes(clientMd5));

	}


	private String hexStringFromBytes(byte[] b) {
		return String.format("%0" + b.length * 2 + "x", new BigInteger(1, b));
	}


	@AfterEach
	public void tearDown() throws Exception {
		integrationServer.shutdown();
		httpClient.shutdown();

	}


	private static class FileServingHttpServlet extends HttpServlet {
		private final int bufferSize;
		private final File serverBinaryBlob;

		public FileServingHttpServlet(int bufferSize, TemporaryFile serverBinaryBlob) {
			this.bufferSize = bufferSize;
			this.serverBinaryBlob = serverBinaryBlob.getFile();
		}

		@Override
		protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
			resp.setBufferSize(bufferSize);

			resp.setContentType("application/octet-stream");
			resp.setHeader("Content-Disposition", "filename=\"serverBinary.blob\"");
			resp.setContentLength((int) serverBinaryBlob.length());
			byte[] buffer = new byte[bufferSize];
			try (FileInputStream fis = new FileInputStream(serverBinaryBlob); OutputStream os = resp.getOutputStream()) {
				int byteRead = 0;
				while ((byteRead = fis.read(buffer)) != -1) {
					os.write(buffer, 0, byteRead);
				}
				os.flush();

				resp.setStatus(200);

			} catch (Exception excp) {
				resp.setStatus(500);

			}
		}
	}


	private static class AsyncFileServingHttpServlet extends HttpServlet {
		private final File serverBinaryBlob;

		public AsyncFileServingHttpServlet(TemporaryFile serverBinaryBlob) {
			this.serverBinaryBlob = serverBinaryBlob.getFile();
		}

		@Override
		protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

			try (RandomAccessFile raf = new RandomAccessFile(serverBinaryBlob, "r")) {
				ByteBuffer buf = raf.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, raf.length());
				final ByteBuffer content = buf.asReadOnlyBuffer();
				final HttpOutput out = (HttpOutput) resp.getOutputStream();
				final AsyncContext async = req.startAsync();
				out.setWriteListener(new WriteListener() {
					@Override
					public void onWritePossible() throws IOException {
						while (out.isReady()) {
							if (!content.hasRemaining()) {
								async.complete();
								return;
							}

							out.write(content);
						}
					}

					@Override
					public void onError(Throwable t) {
						getServletContext().log("Async Error", t);
						async.complete();
					}
				});
			}
		}
	}
}
