/**
 * 
 */
package edu.pitt.sis.ext.colfusion.docker.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;

import edu.pitt.sis.exp.colfusion.utils.PairOf;
import edu.pitt.sis.ext.colfusion.docker.utils.containerProviders.AbstractDockerContainerProvider;

/**
 * @author Evgeny
 *
 */
public class ColfusionDockerClient {

	private static final Logger logger = LogManager.getLogger(ColfusionDockerClient.class.getName());
	
	final DockerClient dockerClient;
	
	public static final String COLFUSION_DOCKER_VERSION = "colfusion.docker.version";
	
	public static final String COLFUSION_DOCKER_URI = "colfusion.docker.uri";
	
	public static final String COLFUSION_DOCKER_SERVER_ADDRESS = "colfusion.docker.server_address";
	
	public static final String COLFUSION_DOCKER_CERT_PATH = "colfusion.docker.cert_path";
	
	final Map<String, AbstractDockerContainerProvider<?>> registeredContainerProviders = new HashMap<String, AbstractDockerContainerProvider<?>>();
	
	private final Properties properties;
	
	public ColfusionDockerClient(final Properties properties) throws IllegalArgumentException {
		this.properties = properties;
		dockerClient = initDockerClient();
	}
	
	/**
	 * 
	 */
	private DockerClient initDockerClient() throws IllegalArgumentException {
		checkIfAllRequiredPropertiesSpecified();
		
		logger.info(String.format("Initializing docker client with version '%s', uri '%s', "
				+ "server address '%s', docker cert path '%s'", 
				properties.getProperty(COLFUSION_DOCKER_VERSION), properties.getProperty(COLFUSION_DOCKER_URI),
				properties.getProperty(COLFUSION_DOCKER_SERVER_ADDRESS), properties.getProperty(COLFUSION_DOCKER_CERT_PATH)));
		
		DockerClientConfig config = DockerClientConfig.createDefaultConfigBuilder()
			    .withVersion(properties.getProperty(COLFUSION_DOCKER_VERSION))
			    .withUri(properties.getProperty(COLFUSION_DOCKER_URI))
			    .withServerAddress(properties.getProperty(COLFUSION_DOCKER_SERVER_ADDRESS))
			    .withDockerCertPath(properties.getProperty(COLFUSION_DOCKER_CERT_PATH))
			    .build();
				
		DockerClient dockerClient = DockerClientBuilder.getInstance(config).build();
		
		logger.info("Done initializing docker client");
		
		return dockerClient;
	}

	void checkIfAllRequiredPropertiesSpecified() 
			throws IllegalArgumentException {
		for (String propertyKey : getPropertyKeys()) {
			if (properties.getProperty(propertyKey) == null) {
				String message = String.format("Not all required properties were set to initialize docker client. "
						+ "%s property is null", propertyKey);
				logger.error(message);
				throw new IllegalArgumentException(message);
			}
		}
		
	}

	public String createContainer(final String imageName,
			final String tag, final PairOf<String, String>[] envVariables) throws IOException {
		
		pullImage(imageName, tag);
		
		CreateContainerCmd createContainerCmd = dockerClient.createContainerCmd(imageName);
		
		if (envVariables != null && envVariables.length > 0) {
			String[] envVariablesParams = new String[envVariables.length];
			for (int i = 0; i < envVariables.length; i++) {
				envVariablesParams[i] = String.format("%s=%s", envVariables[i].getValue1(), envVariables[i].getValue2());
			}
			
			createContainerCmd = createContainerCmd.withEnv(envVariablesParams); // might not need to reassign
		}
		
		CreateContainerResponse container = createContainerCmd.exec();
		
		return container.getId();
	}

	public void pullImage(final String imageName, final String tag) throws IOException {
		InputStream io = dockerClient.pullImageCmd(imageName)
				.withTag(tag)
				.exec();
		
		BufferedReader bf = new BufferedReader(new InputStreamReader(io));
		String line = null;
		//TODO: potential "deadlock" if there never a line that contains that string. Check for mysql shutdown
		while ((line = bf.readLine()) != null) {
			logger.info(line);
		}
		
		bf.close();
		io.close();
	}

	public void startContainer(final String containerId) {
		dockerClient.startContainerCmd(containerId)
		   .withPublishAllPorts(true)
		   .exec();
	}

	public void stopContainer(final String containerId) {
		dockerClient.stopContainerCmd(containerId).exec();
	}
	
	public void deleteContainer(final String containerId) {
		dockerClient.removeContainerCmd(containerId).exec();
	}
	
	public InputStream logContainer(final String containerId) {
		InputStream io = dockerClient.logContainerCmd(containerId)
				.withStdOut(true)
				.withStdErr(true)
				.withTailAll()
				.withFollowStream(true)
				.exec();
		
		return io;
	}

	public InspectContainerResponse inspectContainer(final String containerId) {
		InspectContainerResponse inspectResponse = dockerClient.inspectContainerCmd(containerId).exec();
		
		return inspectResponse;
	}

	/**
	 * Return the docker host IP address.
	 * @return
	 * @throws URISyntaxException 
	 */
	public String getHost() throws URISyntaxException {
		return new URI(properties.getProperty(COLFUSION_DOCKER_URI)).getHost();
	}
	
	/**
	 * Returns property keys that are required to initialize the docker client.
	 * @return
	 */
	public static List<String> getPropertyKeys() {
		return Arrays.asList(COLFUSION_DOCKER_VERSION, COLFUSION_DOCKER_URI, COLFUSION_DOCKER_SERVER_ADDRESS, COLFUSION_DOCKER_CERT_PATH);
	}
}
