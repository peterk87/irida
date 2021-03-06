package ca.corefacility.bioinformatics.irida.config.services;

import ca.corefacility.bioinformatics.irida.config.analysis.AnalysisExecutionServiceConfig;
import ca.corefacility.bioinformatics.irida.config.analysis.ExecutionManagerConfig;
import ca.corefacility.bioinformatics.irida.config.repository.ForbidJpqlUpdateDeletePostProcessor;
import ca.corefacility.bioinformatics.irida.config.repository.IridaApiRepositoriesConfig;
import ca.corefacility.bioinformatics.irida.config.security.IridaApiSecurityConfig;
import ca.corefacility.bioinformatics.irida.config.services.conditions.NreplServerSpringCondition;
import ca.corefacility.bioinformatics.irida.config.services.scheduled.IridaScheduledTasksConfig;
import ca.corefacility.bioinformatics.irida.config.workflow.IridaWorkflowsConfig;
import ca.corefacility.bioinformatics.irida.model.user.Role;
import ca.corefacility.bioinformatics.irida.model.user.User;
import ca.corefacility.bioinformatics.irida.processing.FileProcessingChain;
import ca.corefacility.bioinformatics.irida.processing.FileProcessor;
import ca.corefacility.bioinformatics.irida.processing.impl.*;
import ca.corefacility.bioinformatics.irida.repositories.analysis.submission.AnalysisSubmissionRepository;
import ca.corefacility.bioinformatics.irida.repositories.sample.QCEntryRepository;
import ca.corefacility.bioinformatics.irida.repositories.sequencefile.SequencingObjectRepository;
import ca.corefacility.bioinformatics.irida.service.AnalysisSubmissionCleanupService;
import ca.corefacility.bioinformatics.irida.service.TaxonomyService;
import ca.corefacility.bioinformatics.irida.service.impl.InMemoryTaxonomyService;
import ca.corefacility.bioinformatics.irida.service.impl.analysis.submission.AnalysisSubmissionCleanupServiceImpl;
import ca.corefacility.bioinformatics.irida.service.user.UserService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.matlux.NreplServerSpring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.concurrent.DelegatingSecurityContextScheduledExecutorService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.thymeleaf.spring4.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import javax.validation.Validator;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Configuration for the IRIDA platform.
 * 
 * 
 */
@Configuration
@Import({ IridaApiSecurityConfig.class, IridaApiAspectsConfig.class, IridaApiRepositoriesConfig.class,
		ExecutionManagerConfig.class, AnalysisExecutionServiceConfig.class, IridaWorkflowsConfig.class,
		WebEmailConfig.class, IridaScheduledTasksConfig.class })
@ComponentScan(basePackages = { "ca.corefacility.bioinformatics.irida.service",
		"ca.corefacility.bioinformatics.irida.processing", "ca.corefacility.bioinformatics.irida.pipeline.results" })
public class IridaApiServicesConfig {
	private static final Logger logger = LoggerFactory.getLogger(IridaApiServicesConfig.class);
	
	private static final String DEFAULT_ENCODING = "UTF-8";
	private static final String[] RESOURCE_LOCATIONS = { "classpath:/i18n/messages", "classpath:/i18n/mobile" };
	
	@Autowired
	private Environment env;

	@Value("${taxonomy.location}")
	private ClassPathResource taxonomyFileLocation;

	@Value("${file.processing.decompress}")
	private Boolean decompressFiles;

	@Value("${file.processing.decompress.remove.compressed.file}")
	private Boolean removeCompressedFiles;
	
	// the key + colon syntax allows default values. we use `false` here so we can conditionally show tags on the page with thymeleaf
	@Value("${help.page.title:false}")
	private String helpPageTitle;
	
	@Value("${help.page.url:false}")
	private String helpPageUrl;
	
	@Value("${help.contact.email:false}")
	private String helpEmail;
	
	@Value("${irida.version}")
	private String iridaVersion;
	
	@Value("${file.processing.core.size}")
	private int fpCoreSize;
	
	@Value("${file.processing.max.size}")
	private int fpMaxSize;
	
	@Value("${file.processing.queue.capacity}")
	private int fpQueueCapacity;

	@Value("${irida.debug.nrepl.server.port:#{null}}")
	private Integer nreplPort;
	
	@Bean
	public BeanPostProcessor forbidJpqlUpdateDeletePostProcessor() {
		return new ForbidJpqlUpdateDeletePostProcessor();
	}

	@Bean
	public MessageSource messageSource() {
		logger.info("Configuring ReloadableResourceBundleMessageSource.");
		
		final Properties properties = new Properties();
		properties.setProperty("help.page.title", helpPageTitle);
		properties.setProperty("help.page.url", helpPageUrl);
		properties.setProperty("help.contact.email", helpEmail);
		properties.setProperty("irida.version", iridaVersion);
		
		final ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();

		try {
			final List<String> workflowMessageSources = findWorkflowMessageSources();
			workflowMessageSources.addAll(Arrays.asList(RESOURCE_LOCATIONS));
			final String[] allMessageSources = workflowMessageSources.toArray(new String[workflowMessageSources.size()]);
			logger.debug("Setting message sources basenames: " + Arrays.toString(allMessageSources));
			source.setBasenames(allMessageSources);
		} catch (IOException e) {
			logger.error("Could not set/load workflow message sources. " + e);
			source.setBasenames(RESOURCE_LOCATIONS);
		}

		source.setFallbackToSystemLocale(false);
		source.setDefaultEncoding(DEFAULT_ENCODING);
		source.setCommonMessages(properties);

		// Set template cache timeout if in production
		// Don't cache at all if in development
		if (!env.acceptsProfiles("prod")) {
			source.setCacheSeconds(0);
		}

		return source;
	}

	private List<String> findWorkflowMessageSources() throws IOException {
		final String WORKFLOWS_DIRECTORY = "/ca/corefacility/bioinformatics/irida/model/enums/workflows/";
		final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(this.getClass()
				.getClassLoader());
		final Resource[] resources = resolver.getResources(
				"classpath:" + WORKFLOWS_DIRECTORY + "**/messages_en.properties");
		final Pattern pattern = Pattern.compile(
				String.format("^.+(%s.+\\/messages)_en.properties$", WORKFLOWS_DIRECTORY));
		return Arrays.stream(resources)
				.map(x -> getClasspathResourceBasename(pattern, x))
				.filter(Objects::nonNull)
				.sorted(Comparator.reverseOrder())
				.collect(Collectors.toList());
	}

	private String getClasspathResourceBasename(Pattern pattern, Resource x) {
		try {
			final String path = x.getFile()
					.getAbsolutePath();
			final Matcher matcher = pattern.matcher(path);
			if (matcher.matches() && matcher.groupCount() == 1) {
				return "classpath:" + matcher.group(1);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Bean(name = "uploadFileProcessingChain")
	public FileProcessingChain fileProcessorChain(SequencingObjectRepository sequencingObjectRepository,
			QCEntryRepository qcRepository, GzipFileProcessor gzipFileProcessor,
			FastqcFileProcessor fastQcFileProcessor, AssemblyFileProcessor assemblyFileProcessor,
			ChecksumFileProcessor checksumProcessor, CoverageFileProcessor coverageProcessor,
			SistrTypingFileProcessor sistrTypingFileProcessor) {

		gzipFileProcessor.setRemoveCompressedFiles(removeCompressedFiles);

		final List<FileProcessor> fileProcessors = Lists.newArrayList(checksumProcessor, gzipFileProcessor,
				fastQcFileProcessor, coverageProcessor, assemblyFileProcessor, sistrTypingFileProcessor);

		if (!decompressFiles) {
			logger.info("File decompression is disabled [file.processing.decompress=false]");
			fileProcessors.remove(gzipFileProcessor);
		}

		return new DefaultFileProcessingChain(sequencingObjectRepository, qcRepository, fileProcessors);
	}

	@Bean(name = "fileProcessingChainExecutor")
	public ThreadPoolTaskExecutor fileProcessingChainExecutor() {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setCorePoolSize(fpCoreSize);
		taskExecutor.setMaxPoolSize(fpMaxSize);
		taskExecutor.setQueueCapacity(fpQueueCapacity);
		taskExecutor.setThreadPriority(Thread.MIN_PRIORITY);
		return taskExecutor;
	}


	@Bean
	public Validator validator() {
		ResourceBundleMessageSource validatorMessageSource = new ResourceBundleMessageSource();
		validatorMessageSource.setBasename("ValidationMessages");
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.setValidationMessageSource(validatorMessageSource);
		return validator;
	}

	@Bean
	public TaxonomyService taxonomyService() throws URISyntaxException {
		Path path = Paths.get(taxonomyFileLocation.getPath());
		return new InMemoryTaxonomyService(path);
	}

	/**
	 * Builds a new {@link Executor} for analysis tasks.
	 * 
	 * @param userService
	 *            a reference to the user service.
	 * 
	 * @return A new {@link Executor} for analysis tasks.
	 */
	@Bean
	@DependsOn("springLiquibase")
	public Executor analysisTaskExecutor(UserService userService) {
		ScheduledExecutorService delegateExecutor = Executors.newScheduledThreadPool(4);
		SecurityContext schedulerContext = createAnalysisTaskSecurityContext(userService);
		return new DelegatingSecurityContextScheduledExecutorService(delegateExecutor, schedulerContext);
	}
	
	@Bean
	@DependsOn("springLiquibase")
	@Profile({ "prod" })
	public AnalysisSubmissionCleanupService analysisSubmissionCleanupService(
			AnalysisSubmissionRepository analysisSubmissionRepository, UserService userService) {
		AnalysisSubmissionCleanupService analysisSubmissionCleanupService = new AnalysisSubmissionCleanupServiceImpl(
				analysisSubmissionRepository);
		SecurityContext adminContext = createAnalysisTaskSecurityContext(userService);

		// Run method to clean up previous analysis submissions in inconsistent
		// states.
		SecurityContext oldContext = SecurityContextHolder.getContext();
		SecurityContextHolder.setContext(adminContext);
		analysisSubmissionCleanupService.switchInconsistentSubmissionsToError();
		SecurityContextHolder.setContext(oldContext);

		return analysisSubmissionCleanupService;
	}

	/**
	 * Creates a security context object for the analysis tasks.
	 * 
	 * @param userService
	 *            A {@link UserService}.
	 * 
	 * @return A {@link SecurityContext} for the analysis tasks.
	 */
	private SecurityContext createAnalysisTaskSecurityContext(UserService userService) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();

		Authentication anonymousToken = new AnonymousAuthenticationToken("nobody", "nobody",
				ImmutableList.of(Role.ROLE_ANONYMOUS));

		Authentication oldAuthentication = SecurityContextHolder.getContext().getAuthentication();
		SecurityContextHolder.getContext().setAuthentication(anonymousToken);
		User admin = userService.getUserByUsername("admin");
		SecurityContextHolder.getContext().setAuthentication(oldAuthentication);

		Authentication adminAuthentication = new PreAuthenticatedAuthenticationToken(admin, null,
				Lists.newArrayList(Role.ROLE_ADMIN));

		context.setAuthentication(adminAuthentication);

		return context;
	}

	/**
	 * @return An Executor for handling uploads to Galaxy.
	 */
	@Bean
	public Executor uploadExecutor() {
		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setCorePoolSize(4);
		taskExecutor.setMaxPoolSize(8);
		taskExecutor.setQueueCapacity(16);
		taskExecutor.setThreadPriority(Thread.MIN_PRIORITY);
		return taskExecutor;
	}

	/*
	 * Template engine for constructing ncbi export submissions
	 */
	@Bean(name = "exportUploadTemplateEngine")
	public SpringTemplateEngine exportUploadTemplateEngine() {
		SpringTemplateEngine exportUploadTemplateEngine = new SpringTemplateEngine();

		ClassLoaderTemplateResolver classLoaderTemplateResolver = new ClassLoaderTemplateResolver();
		classLoaderTemplateResolver.setPrefix("/ca/corefacility/bioinformatics/irida/export/");
		classLoaderTemplateResolver.setSuffix(".xml");

		classLoaderTemplateResolver.setTemplateMode(TemplateMode.XML);
		classLoaderTemplateResolver.setCharacterEncoding("UTF-8");

		exportUploadTemplateEngine.addTemplateResolver(classLoaderTemplateResolver);
		return exportUploadTemplateEngine;
	}

	@Bean
	@Profile("dev")
	@Conditional(NreplServerSpringCondition.class)
	public NreplServerSpring nRepl() {
		return new NreplServerSpring(nreplPort);
	}

}

