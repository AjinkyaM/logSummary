package org.aj.logSummary.logSummary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.gson.Gson;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import eu.bitwalker.useragentutils.UserAgent;
import eu.bitwalker.useragentutils.Version;

@RestController
public class LogSummaryController {

	/** The number of fields that must be found. */
	public static final int NUM_FIELDS = 9;

	private static final Logger logger = LoggerFactory.getLogger(LogSummaryController.class);
    
	@Value("${user-name}")
	private String user;
	
	@Value("${user-password}")
	private String password;
	
	@Value("${host}")
	private String host;
	
	private int port = 22;
	
	@Value("${file-name}")
	private String remoteFile;
	
	String logEntryPattern = "^([\\d.]+) (\\S+) (\\S+) \\[([\\w:/]+\\s[+\\-]\\d{4})\\] \"(.+?)\" (\\d{3}) (\\d+) \"([^\"]+)\" \"([^\"]+)\"";
	Pattern p = Pattern.compile(logEntryPattern);

	@RequestMapping("/log-summary-module")
	public String logSummaryModule() {
		Map<String, String> moduleMap = new HashMap<>();
		BufferedReader logBrReader = getLogReader();

		if (null != logBrReader) {

			moduleMap = getModuleMap(logBrReader);

		}

		Gson gson = new Gson();
		return gson.toJson(moduleMap);

	}
	
	@RequestMapping("/log-summary-browser")
	public String logSummaryBrowser() {
		Map<String, String> moduleMap = new HashMap<>();
		BufferedReader logBrReader = getLogReader();

		if (null != logBrReader) {

			moduleMap = getBrowserMap(logBrReader);

		}

		Gson gson = new Gson();
		return gson.toJson(moduleMap);

	}

	private Map<String, String> getBrowserMap(BufferedReader logBrReader) {
		String line;
		Matcher matcher = null;
		Map<String, Integer> browserMap = new HashMap<>();

		try {
			while ((line = logBrReader.readLine()) != null) {
				matcher = p.matcher(line);

				if (!matcher.matches() || NUM_FIELDS != matcher.groupCount()) {
					logger.error("Bad log entry (or problem with RE?):");
					logger.error(line);
					continue;
				}

				UserAgent ua = new UserAgent(matcher.group(9));
				if (null != ua) {
					Version browserVersion = ua.getBrowserVersion();
					String browserName = ua.getBrowser().toString();
					int majVersion = 0;
					if (null != browserVersion) {
						majVersion = Integer.parseInt(browserVersion.getMajorVersion());
					}
					
					if(majVersion != 0) {
						browserMap.merge(browserName + "_" + majVersion + "-counter", 1, Integer::sum);
					}else {
						browserMap.merge(browserName + "-counter", 1, Integer::sum);
					}
					

					if (matcher.group(5).contains("modules")) {
						String[] sectionName = matcher.group(5).substring(matcher.group(5).lastIndexOf("modules") + 8).split("/");

						if (!(sectionName[0].contains("-") || sectionName[0].contains("?")|| sectionName[0].contains("&"))) {
							browserMap.merge(sectionName[0] + "-counter", 1, Integer::sum);
						}

					}
				}

			}
		} catch (IOException e) {
			logger.error(e.getMessage());
		}

		return browserMap.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> Integer.toString(e.getValue())));

	}

	private Map<String, String> getModuleMap(BufferedReader logBrReader) {
		String line;
		Matcher matcher = null;
		Map<String, Integer> sectionMap = new HashMap<>();

		try {
			while ((line = logBrReader.readLine()) != null) {
				matcher = p.matcher(line);

				if (!matcher.matches() || NUM_FIELDS != matcher.groupCount()) {
					logger.error("Bad log entry (or problem with RE?):");
					logger.error(line);
					continue;
				}

				if (matcher.group(5).contains("modules")) {
					String[] sectionName = matcher.group(5).substring(matcher.group(5).lastIndexOf("modules") + 8).split("/");

					if (!(sectionName[0].contains("-") || sectionName[0].contains("?") || sectionName[0].contains("&"))) {
						sectionMap.merge(sectionName[0] + "-counter", 1, Integer::sum);
					}

				}
			}

		} catch (IOException e) {
			logger.error(e.getMessage());
		}

		return sectionMap.entrySet().stream()
				.collect(Collectors.toMap(e -> e.getKey(), e -> Integer.toString(e.getValue())));
	}

	private BufferedReader getLogReader() {
		try {
			JSch jsch = new JSch();
			Session session = jsch.getSession(user, host, port);
			session.setPassword(password);
			session.setConfig("StrictHostKeyChecking", "no");
			logger.info("Establishing Connection...");
			session.connect();
			logger.info("Connection established.");
			logger.info("Creating SFTP Channel.");
			ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
			sftpChannel.connect();
			logger.info("SFTP Channel created.");
			InputStream fis = null;
			fis = sftpChannel.get(remoteFile);

			GZIPInputStream gzip = new GZIPInputStream(fis);

			return new BufferedReader(new InputStreamReader(gzip));

		} catch (JSchException | SftpException | IOException e) {
			logger.error(e.getMessage());
		}

		return null;
	}

}
