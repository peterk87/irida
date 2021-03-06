package ca.corefacility.bioinformatics.irida.ria.web.analysis;

import ca.corefacility.bioinformatics.irida.exceptions.EntityNotFoundException;
import ca.corefacility.bioinformatics.irida.exceptions.ExecutionManagerException;
import ca.corefacility.bioinformatics.irida.exceptions.IridaWorkflowNotFoundException;
import ca.corefacility.bioinformatics.irida.exceptions.PostProcessingException;
import ca.corefacility.bioinformatics.irida.model.enums.AnalysisState;
import ca.corefacility.bioinformatics.irida.model.enums.AnalysisType;
import ca.corefacility.bioinformatics.irida.model.joins.impl.ProjectMetadataTemplateJoin;
import ca.corefacility.bioinformatics.irida.model.project.Project;
import ca.corefacility.bioinformatics.irida.model.sample.MetadataTemplate;
import ca.corefacility.bioinformatics.irida.model.sample.MetadataTemplateField;
import ca.corefacility.bioinformatics.irida.model.sample.Sample;
import ca.corefacility.bioinformatics.irida.model.sample.metadata.MetadataEntry;
import ca.corefacility.bioinformatics.irida.model.sequenceFile.SequenceFilePair;
import ca.corefacility.bioinformatics.irida.model.user.User;
import ca.corefacility.bioinformatics.irida.model.workflow.IridaWorkflow;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.Analysis;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.AnalysisOutputFile;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.JobError;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.ToolExecution;
import ca.corefacility.bioinformatics.irida.model.workflow.submission.AnalysisSubmission;
import ca.corefacility.bioinformatics.irida.model.workflow.submission.ProjectAnalysisSubmissionJoin;
import ca.corefacility.bioinformatics.irida.pipeline.results.AnalysisSubmissionSampleProcessor;
import ca.corefacility.bioinformatics.irida.ria.utilities.FileUtilities;
import ca.corefacility.bioinformatics.irida.ria.web.analysis.dto.AnalysisOutputFileInfo;
import ca.corefacility.bioinformatics.irida.ria.web.components.datatables.DataTablesParams;
import ca.corefacility.bioinformatics.irida.ria.web.components.datatables.DataTablesResponse;
import ca.corefacility.bioinformatics.irida.ria.web.components.datatables.config.DataTablesRequest;
import ca.corefacility.bioinformatics.irida.ria.web.services.AnalysesListingService;
import ca.corefacility.bioinformatics.irida.security.permissions.analysis.UpdateAnalysisSubmissionPermission;
import ca.corefacility.bioinformatics.irida.service.AnalysisSubmissionService;
import ca.corefacility.bioinformatics.irida.service.ProjectService;
import ca.corefacility.bioinformatics.irida.service.SequencingObjectService;
import ca.corefacility.bioinformatics.irida.service.sample.MetadataTemplateService;
import ca.corefacility.bioinformatics.irida.service.sample.SampleService;
import ca.corefacility.bioinformatics.irida.service.user.UserService;
import ca.corefacility.bioinformatics.irida.service.workflow.IridaWorkflowsService;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Controller for Analysis.
 */
@Controller
@RequestMapping("/analysis")
public class AnalysisController {
	private static final Logger logger = LoggerFactory.getLogger(AnalysisController.class);
	// PAGES
	public static final Map<AnalysisType, String> PREVIEWS = ImmutableMap
			.of(AnalysisType.PHYLOGENOMICS, "tree", AnalysisType.SISTR_TYPING, "sistr");
	private static final String BASE = "analysis/";
	public static final String PAGE_DETAILS_DIRECTORY = BASE + "details/";
	public static final String PREVIEW_UNAVAILABLE = PAGE_DETAILS_DIRECTORY + "unavailable";
	public static final String PAGE_ANALYSIS_LIST = "analyses/analyses";

	/*
	 * SERVICES
	 */
	private AnalysisSubmissionService analysisSubmissionService;
	private IridaWorkflowsService workflowsService;
	private MessageSource messageSource;
	private UserService userService;
	private ProjectService projectService;
	private UpdateAnalysisSubmissionPermission updateAnalysisPermission;
	private SampleService sampleService;
	private MetadataTemplateService metadataTemplateService;
	private SequencingObjectService sequencingObjectService;
	private AnalysesListingService analysesListingService;
	private AnalysisSubmissionSampleProcessor analysisSubmissionSampleProcessor;

	@Autowired
	public AnalysisController(AnalysisSubmissionService analysisSubmissionService,
			IridaWorkflowsService iridaWorkflowsService, UserService userService, SampleService sampleService,
			ProjectService projectService, UpdateAnalysisSubmissionPermission updateAnalysisPermission,
			MetadataTemplateService metadataTemplateService, SequencingObjectService sequencingObjectService,
			AnalysesListingService analysesListingService,
			AnalysisSubmissionSampleProcessor analysisSubmissionSampleProcessor, MessageSource messageSource) {
		this.analysisSubmissionService = analysisSubmissionService;
		this.workflowsService = iridaWorkflowsService;
		this.messageSource = messageSource;
		this.userService = userService;
		this.updateAnalysisPermission = updateAnalysisPermission;
		this.sampleService = sampleService;
		this.projectService = projectService;
		this.metadataTemplateService = metadataTemplateService;
		this.sequencingObjectService = sequencingObjectService;
		this.analysesListingService = analysesListingService;
		this.analysisSubmissionSampleProcessor = analysisSubmissionSampleProcessor;
	}

	// ************************************************************************************************
	// PAGES
	// ************************************************************************************************

	/**
	 * Get the admin all {@link Analysis} list page
	 *
	 * @param model Model for view variables
	 * @return Name of the analysis page view
	 */
	@PreAuthorize("hasRole('ROLE_ADMIN')")
	@RequestMapping("/all")
	public String getAdminAnalysisList(Model model) {
		model.addAttribute("userList", false);
		model.addAttribute("ajaxURL", "/analysis/ajax/list/all");
		model.addAttribute("states", AnalysisState.values());
		model.addAttribute("analysisTypes", workflowsService.getRegisteredWorkflowTypes());
		return PAGE_ANALYSIS_LIST;
	}

	/**
	 * Get the user {@link Analysis} list page
	 *
	 * @param model Model for view variables
	 * @return Name of the analysis page view
	 */
	@RequestMapping()
	public String getUserAnalysisList(Model model) {
		model.addAttribute("userList", true);
		model.addAttribute("ajaxURL", "/analysis/ajax/list");
		model.addAttribute("states", AnalysisState.values());
		model.addAttribute("analysisTypes", workflowsService.getRegisteredWorkflowTypes());
		return PAGE_ANALYSIS_LIST;
	}

	/**
	 * View details about an individual analysis submission
	 *
	 * @param submissionId the ID of the submission
	 * @param model        Model for the view
	 * @param locale       User's locale
	 * @return name of the details page view
	 */
	@RequestMapping(value = "/{submissionId}", produces = MediaType.TEXT_HTML_VALUE)
	public String getDetailsPage(@PathVariable Long submissionId, Model model, Locale locale) {
		logger.trace("reading analysis submission " + submissionId);
		AnalysisSubmission submission = analysisSubmissionService.read(submissionId);
		model.addAttribute("analysisSubmission", submission);

		boolean canShareToSamples = false;
		if (submission.getAnalysis() != null) {
			canShareToSamples = analysisSubmissionSampleProcessor
					.hasRegisteredAnalysisSampleUpdater(submission.getAnalysis().getAnalysisType());
		}

		model.addAttribute("canShareToSamples", canShareToSamples);


		UUID workflowUUID = submission.getWorkflowId();
		logger.trace("Workflow ID is " + workflowUUID);

		IridaWorkflow iridaWorkflow;
		try {
			iridaWorkflow = workflowsService.getIridaWorkflow(workflowUUID);
		} catch (IridaWorkflowNotFoundException e) {
			logger.error("Error finding workflow, ", e);
			throw new EntityNotFoundException("Couldn't find workflow for submission " + submission.getId(), e);
		}

		// Get the name of the workflow
		AnalysisType analysisType = iridaWorkflow.getWorkflowDescription()
				.getAnalysisType();
		model.addAttribute("analysisType", analysisType);
		String viewName = getViewForAnalysisType(analysisType);
		String workflowName = messageSource.getMessage("workflow." + analysisType.toString() + ".title", null, locale);
		model.addAttribute("workflowName", workflowName);
		model.addAttribute("version", iridaWorkflow.getWorkflowDescription()
				.getVersion());

		// Input files
		// - Paired
		Set<SequenceFilePair> inputFilePairs = sequencingObjectService.getSequencingObjectsOfTypeForAnalysisSubmission(
				submission, SequenceFilePair.class);
		model.addAttribute("paired_end", inputFilePairs);

		// Check if user can update analysis
		Authentication authentication = SecurityContextHolder.getContext()
				.getAuthentication();
		model.addAttribute("updatePermission", updateAnalysisPermission.isAllowed(authentication, submission));

		if (iridaWorkflow.getWorkflowDescription()
				.requiresReference() && submission.getReferenceFile()
				.isPresent()) {
			logger.debug("Adding reference file to page for submission with id [" + submission.getId() + "].");
			model.addAttribute("referenceFile", submission.getReferenceFile()
					.get());
		} else {
			logger.debug("No reference file required for workflow.");
		}

		/*
		 * Preview information
		 */
		try {
			if (submission.getAnalysisState()
					.equals(AnalysisState.COMPLETED)) {
				if (analysisType.equals(AnalysisType.PHYLOGENOMICS)) {
					tree(submission, model);
				} else if (analysisType.equals(AnalysisType.SISTR_TYPING)) {
					model.addAttribute("sistr", true);
				} else if (analysisType.equals(AnalysisType.BIO_HANSEL)) {
					model.addAttribute("bio_hansel", true);
				}
			}

		} catch (IOException e) {
			logger.error("Couldn't get preview for analysis", e);
		}

		return viewName;
	}

	/**
	 * Update an analysis name
	 *
	 * @param submissionId ID of the submission to update
	 * @param name         name to update the analysis to
	 * @param priority     the priority to update the analysis to.  Note only admins will be allowed to update priority
	 * @param model        model for view
	 * @param locale       locale of the user
	 * @return redirect to the analysis page after update
	 */
	@RequestMapping(value = "/{submissionId}/edit", produces = MediaType.TEXT_HTML_VALUE)
	public String editAnalysis(@PathVariable Long submissionId, @RequestParam String name,
			@RequestParam(required = false, defaultValue = "") AnalysisSubmission.Priority priority, Model model,
			Locale locale) {
		AnalysisSubmission submission = analysisSubmissionService.read(submissionId);

		submission.setName(name);

		boolean error = false;

		try {
			analysisSubmissionService.update(submission);
			// Setting the priority as a separate call as it's not allowed to be updated with the normal update
			if (priority != null) {
				analysisSubmissionService.updatePriority(submission, priority);
			}
		} catch (Exception e) {
			logger.error("Error while updating analysis", e);
			error = true;
		}

		if (error) {
			model.addAttribute("updateError", true);
			return getDetailsPage(submissionId, model, locale);
		}

		return "redirect:/analysis/" + submissionId;
	}

	/**
	 * For an {@link AnalysisSubmission}, get info about each {@link AnalysisOutputFile}
	 *
	 * @param id {@link AnalysisSubmission} id
	 * @return map of info about each {@link AnalysisOutputFile}
	 */
	@RequestMapping(value = "/ajax/{id}/outputs", method = RequestMethod.GET)
	@ResponseBody
	public List<AnalysisOutputFileInfo> getOutputFilesInfo(@PathVariable Long id) {
		AnalysisSubmission submission = analysisSubmissionService.read(id);
		Analysis analysis = submission.getAnalysis();
		Set<String> outputNames = analysis.getAnalysisOutputFileNames();
		return outputNames.stream()
				.map((outputName) -> getAnalysisOutputFileInfo(submission, analysis, outputName))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}

	/**
	 * Get {@link AnalysisOutputFileInfo}.
	 *
	 * @param submission {@link AnalysisSubmission} of {@code analysis}
	 * @param analysis   {@link Analysis} to get {@link AnalysisOutputFile}s from
	 * @param outputName Workflow output name
	 * @return {@link AnalysisOutputFile} info
	 */
	private AnalysisOutputFileInfo getAnalysisOutputFileInfo(AnalysisSubmission submission, Analysis analysis,
			String outputName) {
		final ImmutableSet<String> BLACKLIST_FILE_EXT = ImmutableSet.of("zip");
		// set of file extensions for indicating whether the first line of the file should be read
		final ImmutableSet<String> FILE_EXT_READ_FIRST_LINE = ImmutableSet.of("tsv", "txt", "tabular", "csv", "tab");
		final AnalysisOutputFile aof = analysis.getAnalysisOutputFile(outputName);
		final Long aofId = aof.getId();
		final String aofFilename = aof.getFile()
				.getFileName()
				.toString();
		final String fileExt = FileUtilities.getFileExt(aofFilename);
		if (BLACKLIST_FILE_EXT.contains(fileExt))
		{
			return null;
		}
		final ToolExecution tool = aof.getCreatedByTool();
		final String toolName = tool.getToolName();
		final String toolVersion = tool.getToolVersion();
		final AnalysisOutputFileInfo info = new AnalysisOutputFileInfo();

		info.setId(aofId);
		info.setAnalysisSubmissionId(submission.getId());
		info.setAnalysisId(analysis.getId());
		info.setOutputName(outputName);
		info.setFilename(aofFilename);
		info.setFileSizeBytes(aof.getFile()
				.toFile()
				.length());
		info.setToolName(toolName);
		info.setToolVersion(toolVersion);
		info.setFileExt(fileExt);
		if (FILE_EXT_READ_FIRST_LINE.contains(fileExt)) {
			addFirstLine(info, aof);
		}
		return info;
	}

	/**
	 * Add the {@code firstLine} and {@code filePointer} file byte position after reading the first line of an {@link AnalysisOutputFile} to a {@link AnalysisOutputFileInfo} object.
	 *
	 * @param info Object to add {@code firstLine} and {@code filePointer} info to
	 * @param aof {@link AnalysisOutputFile} to read from
	 */
	private void addFirstLine(AnalysisOutputFileInfo info, AnalysisOutputFile aof) {
		RandomAccessFile reader = null;
		final Path aofFile = aof.getFile();
		try {
			reader = new RandomAccessFile(aofFile.toFile(), "r");
			info.setFirstLine(reader.readLine());
			info.setFilePointer(reader.getFilePointer());
		} catch (FileNotFoundException e) {
			logger.error("Could not find file '" + aofFile + "' " + e);
		} catch (IOException e) {
			logger.error("Could not read file '" + aofFile + "' " + e);
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
				logger.error("Could not close file handle for '" + aofFile + "' " + e);
			}
		}
	}

	/**
	 * Read some lines or text from an {@link AnalysisOutputFile}.
	 *
	 * @param id       {@link AnalysisSubmission} id
	 * @param fileId   {@link AnalysisOutputFile} id
	 * @param limit    Optional limit to number of lines to read from file
	 * @param start    Optional line to start reading from
	 * @param end      Optional line to stop reading at
	 * @param seek     Optional file byte position to seek to and begin reading
	 * @param chunk    Optional number of bytes to read from file
	 * @param response HTTP response object
	 * @return JSON with file text or lines as well as information about the file.
	 */
	@RequestMapping(value = "/ajax/{id}/outputs/{fileId}", method = RequestMethod.GET)
	@ResponseBody
	public AnalysisOutputFileInfo getOutputFile(@PathVariable Long id, @PathVariable Long fileId,
			@RequestParam(defaultValue = "100", required = false) Long limit,
			@RequestParam(required = false) Long start, @RequestParam(required = false) Long end,
			@RequestParam(defaultValue = "0", required = false) Long seek, @RequestParam(required = false) Long chunk,
			HttpServletResponse response) {
		AnalysisSubmission submission = analysisSubmissionService.read(id);
		Analysis analysis = submission.getAnalysis();
		final Optional<AnalysisOutputFile> analysisOutputFile = analysis.getAnalysisOutputFiles()
				.stream()
				.filter(x -> Objects.equals(x.getId(), fileId))
				.findFirst();
		if (analysisOutputFile.isPresent()) {
			final AnalysisOutputFile aof = analysisOutputFile.get();
			final Path aofFile = aof.getFile();
			final ToolExecution tool = aof.getCreatedByTool();
			final AnalysisOutputFileInfo contents = new AnalysisOutputFileInfo();
			contents.setId(aof.getId());
			contents.setAnalysisSubmissionId(submission.getId());
			contents.setAnalysisId(analysis.getId());
			contents.setFilename(aofFile.getFileName()
					.toString());
			contents.setFileExt(FileUtilities.getFileExt(aofFile.getFileName()
					.toString()));
			contents.setFileSizeBytes(aof.getFile()
					.toFile()
					.length());
			contents.setToolName(tool.getToolName());
			contents.setToolVersion(tool.getToolVersion());
			try {
				final File file = aofFile.toFile();
				final RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
				randomAccessFile.seek(seek);
				if (seek == 0) {
					if (chunk != null && chunk > 0) {
						contents.setText(FileUtilities.readChunk(randomAccessFile, seek, chunk));
						contents.setChunk(chunk);
						contents.setStartSeek(seek);
					} else {
						final BufferedReader reader = new BufferedReader(new FileReader(randomAccessFile.getFD()));
						final List<String> lines = FileUtilities.readLinesLimit(reader, limit, start, end);
						contents.setLines(lines);
						contents.setLimit((long) lines.size());
						contents.setStart(start);
						contents.setEnd(start + lines.size());
					}
				} else {
					if (chunk != null && chunk > 0) {
						contents.setText(FileUtilities.readChunk(randomAccessFile, seek, chunk));
						contents.setChunk(chunk);
						contents.setStartSeek(seek);
					} else {
						final List<String> lines = FileUtilities.readLinesFromFilePointer(randomAccessFile, limit);
						contents.setLines(lines);
						contents.setStartSeek(seek);
						contents.setStart(start);
						contents.setLimit((long) lines.size());
					}
				}
				contents.setFilePointer(randomAccessFile.getFilePointer());
			} catch (IOException e) {
				logger.error("Could not read output file '" + aof.getId() + "' " + e);
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				contents.setError("Could not read output file");

			}
			return contents;
		} else {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			return null;
		}
	}

	/**
	 * Get a map with list of {@link JobError} for an {@link AnalysisSubmission} under key `jobErrors`
	 * @param submissionId {@link AnalysisSubmission} id
	 * @return map with list of {@link JobError} under key `jobErrors`
	 */
	@RequestMapping(value = "/ajax/{submissionId}/job-errors", method = RequestMethod.GET)
	@ResponseBody
	public ImmutableMap<String, Object> getJobErrors(@PathVariable Long submissionId) {
		try {
			List<JobError> jobErrors = analysisSubmissionService.getJobErrors(submissionId);
			if (jobErrors != null && !jobErrors.isEmpty()) {
				return ImmutableMap.of("jobErrors", jobErrors);
			}
		} catch (ExecutionManagerException e) {
			logger.error("Error " + e);
		}
		return ImmutableMap.of("error", "No JobErrors for AnalysisSubmission [id=" + submissionId + "]");
	}

	/**
	 * Get the status of projects that can be shared with the given analysis
	 *
	 * @param submissionId
	 *            the {@link AnalysisSubmission} id
	 * @return a list of {@link AnalysisController.SharedProjectResponse}
	 */
	@RequestMapping(value = "/ajax/{submissionId}/share", method = RequestMethod.GET)
	@ResponseBody
	public List<SharedProjectResponse> getSharedProjectsForAnalysis(@PathVariable Long submissionId) {
		AnalysisSubmission submission = analysisSubmissionService.read(submissionId);
		// Input files
		// - Paired
		Set<SequenceFilePair> inputFilePairs = sequencingObjectService.getSequencingObjectsOfTypeForAnalysisSubmission(
				submission, SequenceFilePair.class);

		// get projects already shared with submission
		Set<Project> projectsShared = projectService.getProjectsForAnalysisSubmission(submission)
				.stream()
				.map(ProjectAnalysisSubmissionJoin::getSubject)
				.collect(Collectors.toSet());

		// get available projects
		Set<Project> projectsInAnalysis = projectService.getProjectsForSequencingObjects(inputFilePairs);

		List<SharedProjectResponse> projectResponses = projectsShared.stream()
				.map(p -> new SharedProjectResponse(p, true))
				.collect(Collectors.toList());

		// Create response for shared projects
		projectResponses.addAll(projectsInAnalysis.stream()
				.filter(p -> !projectsShared.contains(p))
				.map(p -> new SharedProjectResponse(p, false))
				.collect(Collectors.toList()));

		projectResponses.sort(new Comparator<SharedProjectResponse>() {

			@Override
			public int compare(SharedProjectResponse p1, SharedProjectResponse p2) {
				return p1.getProject()
						.getName()
						.compareTo(p2.getProject()
								.getName());
			}
		});

		return projectResponses;
	}

	/**
	 * Update the share status of a given {@link AnalysisSubmission} for a given
	 * {@link Project}
	 *
	 * @param submissionId the {@link AnalysisSubmission} id to share/unshare
	 * @param projectId    the {@link Project} id to share with
	 * @param shareStatus  whether or not to share the {@link AnalysisSubmission}
	 * @param locale       Locale of the logged in user
	 * @return Success message if successful
	 */
	@RequestMapping(value = "/ajax/{submissionId}/share", method = RequestMethod.POST)
	public Map<String, String> updateProjectShare(@PathVariable Long submissionId,
			@RequestParam("project") Long projectId, @RequestParam("shared") boolean shareStatus, Locale locale) {
		AnalysisSubmission submission = analysisSubmissionService.read(submissionId);
		Project project = projectService.read(projectId);

		String message = "";
		if (shareStatus) {
			analysisSubmissionService.shareAnalysisSubmissionWithProject(submission, project);

			message = messageSource.getMessage("analysis.details.share.enable", new Object[] { project.getLabel() },
					locale);
		} else {
			analysisSubmissionService.removeAnalysisProjectShare(submission, project);
			message = messageSource.getMessage("analysis.details.share.remove", new Object[] { project.getLabel() },
					locale);
		}

		return ImmutableMap.of("result", "success", "message", message);
	}

	/**
	 * Save the results of an analysis back to the samples
	 *
	 * @param submissionId ID of the {@link AnalysisSubmission}
	 * @param locale       locale of the logged in user
	 * @return success message
	 */
	@RequestMapping(value = "/ajax/{submissionId}/save-results", method = RequestMethod.POST)
	@ResponseBody
	public Map<String, String> saveResultsToSamples(@PathVariable Long submissionId, Locale locale) {
		AnalysisSubmission submission = analysisSubmissionService.read(submissionId);

		if(submission.getUpdateSamples()){
			String message = messageSource.getMessage("analysis.details.save.alreadysavederror", null, locale);
			return ImmutableMap.of("result", "error", "message", message);
		}

		try {
			analysisSubmissionSampleProcessor.updateSamples(submission);

			submission.setUpdateSamples(true);
			analysisSubmissionService.update(submission);
		} catch (PostProcessingException e) {
			String message = messageSource.getMessage("analysis.details.save.processingerror", null, locale);
			return ImmutableMap.of("result", "error", "message", message);
		}

		String message = messageSource.getMessage("analysis.details.save.response", null, locale);

		return ImmutableMap.of("result", "success", "message", message);
	}

	/**
	 * Get the page for viewing advanced phylogenetic visualization
	 *
	 * @param submissionId {@link Long} identifier for an {@link AnalysisSubmission}
	 * @param model        {@link Model}
	 * @return {@link String} path to the page template.
	 */
	@RequestMapping("/{submissionId}/advanced-phylo")
	public String getAdvancedPhylogeneticVisualizationPage(@PathVariable Long submissionId, Model model) {

		AnalysisSubmission submission = analysisSubmissionService.read(submissionId);

		model.addAttribute("submissionId", submissionId);
		model.addAttribute("submission", submission);
		return BASE + "visualizations/phylocanvas-metadata";
	}

	// ************************************************************************************************
	// Analysis view setup
	// ************************************************************************************************

	/**
	 * Construct the model parameters for an {@link AnalysisType#PHYLOGENOMICS}
	 * {@link Analysis}
	 *
	 * @param submission The analysis submission
	 * @param model      The model to add parameters
	 * @throws IOException If the tree file couldn't be read
	 */
	private void tree(AnalysisSubmission submission, Model model) throws IOException {
		final String treeFileKey = "tree";

		Analysis analysis = submission.getAnalysis();
		AnalysisOutputFile file = analysis.getAnalysisOutputFile(treeFileKey);
		List<String> lines = Files.readAllLines(file.getFile());
		model.addAttribute("analysis", analysis);
		model.addAttribute("newick", lines.get(0));

		// inform the view to display the tree preview
		model.addAttribute("preview", "tree");
	}

	/**
	 * DataTables request handler for an Administrator listing all {@link AnalysisSubmission}
	 *
	 * @param params {@link DataTablesParams}
	 * @param locale {@link Locale}
	 * @return {@link DataTablesResponse}
	 * @throws IridaWorkflowNotFoundException If the requested workflow doesn't exist
	 * @throws EntityNotFoundException        If the submission cannot be found
	 * @throws ExecutionManagerException      If the submission cannot be read properly
	 */
	@RequestMapping(value = "/ajax/list/all", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseBody
	public DataTablesResponse getSubmissions(@DataTablesRequest DataTablesParams params, Locale locale)
			throws IridaWorkflowNotFoundException, EntityNotFoundException, ExecutionManagerException {
		return analysesListingService.getPagedSubmissions(params, locale, null, null);
	}

	/**
	 * DataTables request handler for a User listing all {@link AnalysisSubmission}
	 *
	 * @param params    {@link DataTablesParams}
	 * @param principal {@link Principal}
	 * @param locale    {@link Locale}
	 * @return {@link DataTablesResponse}
	 * @throws IridaWorkflowNotFoundException If the requested workflow doesn't exist
	 * @throws EntityNotFoundException        If the submission cannot be found
	 * @throws ExecutionManagerException      If the submission cannot be read properly
	 */
	@RequestMapping("/ajax/list")
	@ResponseBody
	public DataTablesResponse getSubmissionsForUser(@DataTablesRequest DataTablesParams params, Principal principal,
			Locale locale) throws IridaWorkflowNotFoundException, EntityNotFoundException, ExecutionManagerException {
		User user = userService.getUserByUsername(principal.getName());
		return analysesListingService.getPagedSubmissions(params, locale, user, null);
	}

	/**
	 * DataTables request handler for a User listing all {@link AnalysisSubmission}
	 *
	 * @param params    {@link DataTablesParams}
	 * @param projectId {@link Long}
	 * @param principal {@link Principal}
	 * @param locale    {@link Locale}
	 * @return {@link DataTablesResponse}
	 * @throws IridaWorkflowNotFoundException If the requested workflow doesn't exist
	 * @throws ExecutionManagerException      If the submission cannot be read properly
	 */
	@RequestMapping("/ajax/project/{projectId}/list")
	@ResponseBody
	public DataTablesResponse getSubmissionsForProject(@DataTablesRequest DataTablesParams params,
			@PathVariable Long projectId, Principal principal, Locale locale)
			throws IridaWorkflowNotFoundException, ExecutionManagerException {
		Project project = projectService.read(projectId);
		return analysesListingService.getPagedSubmissions(params, locale, null, project);
	}

	/**
	 * Get the sistr analysis information to display
	 *
	 * @param id ID of the analysis submission
	 * @return Json results for the SISTR analysis
	 */
	@SuppressWarnings("resource")
	@RequestMapping("/ajax/sistr/{id}")
	@ResponseBody
	public Map<String,Object> getSistrAnalysis(@PathVariable Long id) {
		AnalysisSubmission submission = analysisSubmissionService.read(id);
		Collection<Sample> samples = sampleService.getSamplesForAnalysisSubmission(submission);
		Map<String, Object> result = ImmutableMap.of("parse_results_error", true);

		final String sistrFileKey = "sistr-predictions";

		// Get details about the workflow
		UUID workflowUUID = submission.getWorkflowId();
		IridaWorkflow iridaWorkflow;
		try {
			iridaWorkflow = workflowsService.getIridaWorkflow(workflowUUID);
		} catch (IridaWorkflowNotFoundException e) {
			logger.error("Error finding workflow, ", e);
			throw new EntityNotFoundException("Couldn't find workflow for submission " + submission.getId(), e);
		}
		AnalysisType analysisType = iridaWorkflow.getWorkflowDescription()
				.getAnalysisType();
		if (analysisType.equals(AnalysisType.SISTR_TYPING)) {
			Analysis analysis = submission.getAnalysis();
			Path path = analysis.getAnalysisOutputFile(sistrFileKey)
					.getFile();
			try {
				String json = new Scanner(new BufferedReader(new FileReader(path.toFile()))).useDelimiter("\\Z")
						.next();

				// verify file is proper json file
				ObjectMapper mapper = new ObjectMapper();
				List<Map<String, Object>> sistrResults = mapper.readValue(json,
						new TypeReference<List<Map<String, Object>>>() {
						});

				if (sistrResults.size() > 0) {
					// should only ever be one sample for these results
					if (samples.size() == 1) {
						Sample sample = samples.iterator()
								.next();
						result = sistrResults.get(0);

						result.put("parse_results_error", false);

						result.put("sample_name", sample.getSampleName());
					} else {
						logger.error("Invalid number of associated samples for submission " + submission);
					}
				} else {
					logger.error("SISTR results for file [" + path + "] are not correctly formatted");
				}
			} catch (FileNotFoundException e) {
				logger.error("File [" + path + "] not found", e);
			} catch (JsonParseException | JsonMappingException e) {
				logger.error("Error attempting to parse file [" + path + "] as JSON", e);
			} catch (IOException e) {
				logger.error("Error reading file [" + path + "]", e);
			}
		}
		return result;
	}

	// ************************************************************************************************
	// AJAX
	// ************************************************************************************************

	/**
	 * Delete an {@link AnalysisSubmission} by id.
	 *
	 * @param analysisSubmissionId the submission ID to delete.
	 * @param locale               Locale of the logged in user
	 * @return A message stating the submission was deleted
	 */
	@RequestMapping("/ajax/delete/{analysisSubmissionId}")
	@ResponseBody
	public Map<String, String> deleteAjaxAnalysisSubmission(@PathVariable Long analysisSubmissionId,
			final Locale locale) {
		final AnalysisSubmission deletedSubmission = analysisSubmissionService.read(analysisSubmissionId);
		analysisSubmissionService.delete(analysisSubmissionId);
		return ImmutableMap.of("result",
				messageSource.getMessage("analysis.delete.message", new Object[] { deletedSubmission.getLabel() },
						locale));
	}

	/**
	 * Download all output files from an {@link AnalysisSubmission}
	 *
	 * @param analysisSubmissionId Id for a {@link AnalysisSubmission}
	 * @param response             {@link HttpServletResponse}
	 */
	@RequestMapping(value = "/ajax/download/{analysisSubmissionId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public void getAjaxDownloadAnalysisSubmission(@PathVariable Long analysisSubmissionId,
			HttpServletResponse response) {
		AnalysisSubmission analysisSubmission = analysisSubmissionService.read(analysisSubmissionId);

		Analysis analysis = analysisSubmission.getAnalysis();
		Set<AnalysisOutputFile> files = analysis.getAnalysisOutputFiles();
		FileUtilities.createAnalysisOutputFileZippedResponse(response, analysisSubmission.getName(), files);
	}

	/**
	 * Download single output files from an {@link AnalysisSubmission}
	 *
	 * @param analysisSubmissionId Id for a {@link AnalysisSubmission}
	 * @param fileId               the id of the file to download
	 * @param response             {@link HttpServletResponse}
	 */
	@RequestMapping(value = "/ajax/download/{analysisSubmissionId}/file/{fileId}")
	public void getAjaxDownloadAnalysisSubmissionIndividualFile(@PathVariable Long analysisSubmissionId,
			@PathVariable Long fileId, HttpServletResponse response) {
		AnalysisSubmission analysisSubmission = analysisSubmissionService.read(analysisSubmissionId);

		Analysis analysis = analysisSubmission.getAnalysis();
		Set<AnalysisOutputFile> files = analysis.getAnalysisOutputFiles();

		Optional<AnalysisOutputFile> optFile = files.stream()
				.filter(f -> f.getId()
						.equals(fileId))
				.findAny();
		if (!optFile.isPresent()) {
			throw new EntityNotFoundException("Could not find file with id " + fileId);
		}

		FileUtilities.createSingleFileResponse(response, optFile.get());
	}

	/**
	 * Get the current status for a given {@link AnalysisSubmission}
	 *
	 * @param submissionId The {@link UUID} id for a given {@link AnalysisSubmission}
	 * @param locale       The users current {@link Locale}
	 * @return {@link HashMap} containing the status and the percent complete for the {@link AnalysisSubmission}
	 */
	@RequestMapping(value = "/ajax/status/{submissionId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public @ResponseBody
	Map<String, String> getAjaxStatusUpdateForAnalysisSubmission(@PathVariable Long submissionId, Locale locale) {
		Map<String, String> result = new HashMap<>();
		AnalysisSubmission analysisSubmission = analysisSubmissionService.read(submissionId);
		AnalysisState state = analysisSubmission.getAnalysisState();
		result.put("state", state.toString());
		result.put("stateLang", messageSource.getMessage("analysis.state." + state.toString(), null, locale));
		if (!state.equals(AnalysisState.ERROR)) {
			float percentComplete = 0;
			try {
				percentComplete = analysisSubmissionService.getPercentCompleteForAnalysisSubmission(
						analysisSubmission.getId());
				result.put("percentComplete", Float.toString(percentComplete));
			} catch (ExecutionManagerException e) {
				logger.error("Error getting the percentage complete", e);
				result.put("percentageComplete", "");
			}
		}
		return result;
	}

	/**
	 * Get a newick file associated with a specific {@link AnalysisSubmission}.
	 *
	 * @param submissionId {@link Long} id for an {@link AnalysisSubmission}
	 * @return {@link Map} containing the newick file contents.
	 * @throws IOException {@link IOException} if the newick file is not found
	 */
	@RequestMapping("/ajax/{submissionId}/newick")
	@ResponseBody
	public Map<String, Object> getNewickForAnalysis(@PathVariable Long submissionId) throws IOException {
		final String treeFileKey = "tree";

		AnalysisSubmission submission = analysisSubmissionService.read(submissionId);
		Analysis analysis = submission.getAnalysis();
		AnalysisOutputFile file = analysis.getAnalysisOutputFile(treeFileKey);
		List<String> lines = Files.readAllLines(file.getFile());
		return ImmutableMap.of("newick", lines.get(0));
	}

	/**
	 * Get the metadata associated with a template for an analysis.
	 *
	 * @param submissionId {@link Long} identifier for the {@link AnalysisSubmission}
	 * @return {@link Map}
	 */
	@RequestMapping("/ajax/{submissionId}/metadata")
	@ResponseBody
	public Map<String, Object> getMetadataForAnalysisSamples(@PathVariable Long submissionId) {
		AnalysisSubmission submission = analysisSubmissionService.read(submissionId);
		Collection<Sample> samples = sampleService.getSamplesForAnalysisSubmission(submission);

		// Let's get a list of all the metadata available that is unique.
		Set<String> terms = new HashSet<>();
		for (Sample sample : samples) {
			if (!sample.getMetadata().isEmpty()) {
				Map<MetadataTemplateField, MetadataEntry> metadata = sample.getMetadata();
				terms.addAll(
						metadata.keySet().stream().map(MetadataTemplateField::getLabel).collect(Collectors.toSet()));
			}
		}

		// Get the metadata for the samples;
		Map<String, Object> metadata = new HashMap<>();
		for (Sample sample : samples) {
			Map<MetadataTemplateField, MetadataEntry> sampleMetadata = sample.getMetadata();
			Map<String, MetadataEntry> stringMetadata = new HashMap<>();
			sampleMetadata.entrySet().forEach(e -> {
				stringMetadata.put(e.getKey().getLabel(), e.getValue());
			});

			Map<String, MetadataEntry> valuesMap = new HashMap<>();
			for (String term : terms) {

				MetadataEntry value = stringMetadata.get(term);
				if (value == null) {
					// Not all samples will have the same metadata associated with it.  If a sample
					// is missing one of the terms, just give it an empty string.
					value = new MetadataEntry("", "text");
				}

				valuesMap.put(term, value);
			}
			metadata.put(sample.getLabel(), valuesMap);
		}

		return ImmutableMap.of(
				"terms", terms,
				"metadata", metadata
		);
	}

	/**
	 * Get a list of all {@link MetadataTemplate}s for the {@link AnalysisSubmission}
	 *
	 * @param submissionId id of the {@link AnalysisSubmission}
	 * @return a map of {@link MetadataTemplate}s
	 */
	@RequestMapping("/ajax/{submissionId}/metadata-templates")
	@ResponseBody
	public Map<String, Object> getMetadataTemplatesForAnalysis(@PathVariable Long submissionId) {
		AnalysisSubmission submission = analysisSubmissionService.read(submissionId);
		List<Project> projectsUsedInAnalysisSubmission = projectService.getProjectsUsedInAnalysisSubmission(submission);

		Set<Long> projectIds = new HashSet<>();
		Set<Map<String, Object>> templates = new HashSet<>();

		for (Project project : projectsUsedInAnalysisSubmission) {
			if (!projectIds.contains(project.getId())) {
				projectIds.add(project.getId());

				// Get the templates for the project
				List<ProjectMetadataTemplateJoin> templateList = metadataTemplateService
						.getMetadataTemplatesForProject(project);
				for (ProjectMetadataTemplateJoin projectMetadataTemplateJoin : templateList) {
					MetadataTemplate metadataTemplate = projectMetadataTemplateJoin.getObject();
					Map<String, Object> templateMap = ImmutableMap.of("label", metadataTemplate.getLabel(), "id",
							metadataTemplate.getId());
					templates.add(templateMap);
				}
			}
		}

		return ImmutableMap.of("templates", templates);
	}

	/**
	 * Generates a list of metadata fields for a five template.
	 *
	 * @param templateId {@link Long} id for the {@link MetadataTemplate} that the fields are required.
	 * @return {@link Map}
	 */
	@RequestMapping("/ajax/{submissionId}/metadata-template-fields")
	@ResponseBody
	public Map<String, Object> getMetadataTemplateFields(@RequestParam Long templateId) {
		MetadataTemplate template = metadataTemplateService.read(templateId);
		List<MetadataTemplateField> metadataFields = template.getFields();
		List<String> fields = new ArrayList<>();
		for (MetadataTemplateField metadataField : metadataFields) {
			fields.add(metadataField.getLabel());
		}
		return ImmutableMap.of("fields", fields);
	}

	/**
	 * Get the view name for different analysis types
	 *
	 * @param type The {@link AnalysisType}
	 * @return the view name to display
	 */
	private String getViewForAnalysisType(AnalysisType type) {
		String viewName = null;
		if (PREVIEWS.containsKey(type)) {
			viewName = PAGE_DETAILS_DIRECTORY + PREVIEWS.get(type);
		} else {
			viewName = PREVIEW_UNAVAILABLE;
		}

		return viewName;
	}

	/**
	 * Response object storing a project and whether or not it's shared with a
	 * given {@link AnalysisSubmission}
	 */
	@SuppressWarnings("unused")
	private class SharedProjectResponse {
		private Project project;
		private boolean shared;

		public SharedProjectResponse(Project project, boolean shared) {
			this.project = project;
			this.shared = shared;
		}

		public Project getProject() {
			return project;
		}

		public boolean isShared() {
			return shared;
		}
	}
}
