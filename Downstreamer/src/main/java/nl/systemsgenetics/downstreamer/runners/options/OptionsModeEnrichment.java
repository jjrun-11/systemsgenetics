/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.systemsgenetics.downstreamer.runners.options;

import htsjdk.tribble.SimpleFeature;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import nl.systemsgenetics.downstreamer.pathway.PathwayDatabase;
import static nl.systemsgenetics.downstreamer.runners.options.OptionsBase.OPTIONS;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author patri
 */
public class OptionsModeEnrichment extends OptionsBase {

	private static final Logger LOGGER = LogManager.getLogger(OptionsBase.class);

	private final File covariates;
	private final List<PathwayDatabase> pathwayDatabases;
	private final boolean forceNormalGenePvalues;
	private final boolean forceNormalPathwayPvalues;
	private final boolean regressGeneLengths;
	private final File geneInfoFile;
	private final File singleGwasFile;
	private final String gwasPvalueMatrixPath;
	private final boolean excludeHla;
	private final boolean skipPvalueToZscore;

	static {

		OptionBuilder.withArgName("boolean");
		OptionBuilder.withDescription("Linearly correct for the effect of gene lengths on gene p-values");
		OptionBuilder.withLongOpt("regress-gene-lengths");
		OPTIONS.addOption(OptionBuilder.create("rgl"));

		OptionBuilder.withArgName("boolean");
		OptionBuilder.withDescription("Exclude HLA locus during pathway enrichments (chr6 20mb - 40mb)");
		OptionBuilder.withLongOpt("excludeHla");
		OPTIONS.addOption(OptionBuilder.create("eh"));

		OptionBuilder.withArgName("boolean");
		OptionBuilder.withDescription("Force normal gene p-values before pathway enrichtment");
		OptionBuilder.withLongOpt("forceNormalGenePvalues");
		OPTIONS.addOption(OptionBuilder.create("fngp"));

		OptionBuilder.withArgName("boolean");
		OptionBuilder.withDescription("Force normal pathway p-values before pathway enrichtment");
		OptionBuilder.withLongOpt("forceNormalPathwayPvalues");
		OPTIONS.addOption(OptionBuilder.create("fnpp"));

		OptionBuilder.withArgName("boolean");
		OptionBuilder.withDescription("Correct the GWAS for the lambda inflation");
		OptionBuilder.withLongOpt("correctLambda");
		OPTIONS.addOption(OptionBuilder.create("cl"));

		OptionBuilder.withArgName("name=path");
		OptionBuilder.hasArgs();
		OptionBuilder.withValueSeparator();
		OptionBuilder.withDescription("Pathway databases, binary matrix with either z-scores for predicted gene pathway associations or 0 / 1 for gene assignments");
		OptionBuilder.withLongOpt("pathwayDatabase");
		OptionBuilder.isRequired();
		OPTIONS.addOption(OptionBuilder.create("pd"));

		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("File with gene info. col1: geneName (ensg) col2: chr col3: startPos col4: stopPos col5: geneType col6: chrArm");
		OptionBuilder.withLongOpt("genes");
		OptionBuilder.isRequired();
		OPTIONS.addOption(OptionBuilder.create("ge"));

		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("File with covariates used to correct the gene p-values. Works in conjunction with -rgl. Residuals of this regression are used as input for the GLS");
		OptionBuilder.withLongOpt("covariates");
		OPTIONS.addOption(OptionBuilder.create("cov"));

		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("GWAS gene p-values. Rows genes, Cols: genes, pvalue, nSNPs, min SNP p-value. Tab seperated txt or txt.gz file or .dat / datg binary matrix format");
		OptionBuilder.withLongOpt("gwas");
		OPTIONS.addOption(OptionBuilder.create("g"));

		OptionBuilder.withArgName("path");
		OptionBuilder.hasArg();
		OptionBuilder.withDescription("Expects 3 files with equal and same ordered columns (traits) and rows (genes). <path>_pvalues with gene p-values; <path>_nvar number of variants per gene; <path>_minVarPvalue. Each file is Tab seperated txt/txt.gz file or .dat / datg binary matrix format. The number of variants per gene is assumed to be equal, it is allowed ot have only a single column in this file.");
		OptionBuilder.withLongOpt("gwasMatrix");
		OPTIONS.addOption(OptionBuilder.create("gm"));

		OptionBuilder.withArgName("boolean");
		OptionBuilder.withDescription("Exclude HLA locus during pathway enrichments (chr6 20mb - 40mb)");
		OptionBuilder.withLongOpt("excludeHla");
		OPTIONS.addOption(OptionBuilder.create("eh"));
		
		OptionBuilder.withArgName("boolean");
		OptionBuilder.withDescription("Set true to skip converting gene p-values to z-scores. Only do this if the gene scores are already z-scores");
		OptionBuilder.withLongOpt("noPvalueToZscore");
		OPTIONS.addOption(OptionBuilder.create("nptz"));

	}

	public OptionsModeEnrichment(String[] args) throws ParseException {
		super(args);

		// Parse arguments
		final CommandLineParser parser = new PosixParser();
		final CommandLine commandLine = parser.parse(OPTIONS, args, false);

		if (commandLine.hasOption("cov")) {
			covariates = new File(commandLine.getOptionValue("cov"));
		} else {
			covariates = null;
		}

		pathwayDatabases = parsePd(commandLine, "pd", "pathwayDatabase");
		forceNormalGenePvalues = commandLine.hasOption("fngp");
		forceNormalPathwayPvalues = commandLine.hasOption("fnpp");
		regressGeneLengths = commandLine.hasOption("rgl");
		skipPvalueToZscore = commandLine.hasOption("nptz");
		geneInfoFile = new File(commandLine.getOptionValue("ge"));
		
		if (commandLine.hasOption("g") && commandLine.hasOption("gm")) {
			throw new ParseException("Provide either -g or -gm but not both");
		} else if (commandLine.hasOption("g")) {
			singleGwasFile = new File(commandLine.getOptionValue('g'));
			gwasPvalueMatrixPath = null;
		} else if (commandLine.hasOption("gm")) {
			singleGwasFile = null;
			gwasPvalueMatrixPath = commandLine.getOptionValue("gm");
		} else {
			throw new ParseException("Provide either -g or -gm");
		}

		excludeHla = commandLine.hasOption("eh");

		printOptions();

	}

	private static List<PathwayDatabase> parsePd(final CommandLine commandLine, String option, String optionLong) throws ParseException {

		final List<PathwayDatabase> pathwayDatabasesTmp;

		if (commandLine.hasOption(option)) {

			String[] pdValues = commandLine.getOptionValues(option);

			if (pdValues.length % 2 != 0) {
				throw new ParseException("Error parsing --" + optionLong + ". Must be in name=database format");
			}

			final HashSet<String> duplicateChecker = new HashSet<>();
			pathwayDatabasesTmp = new ArrayList<>();

			for (int i = 0; i < pdValues.length; i += 2) {

				if (!duplicateChecker.add(pdValues[i])) {
					throw new ParseException("Error parsing --" + optionLong + ". Duplicate database name found");
				}

				pathwayDatabasesTmp.add(new PathwayDatabase(pdValues[i], pdValues[i + 1]));

			}

		} else {

			pathwayDatabasesTmp = Collections.emptyList();

		}

		return pathwayDatabasesTmp;
	}

	@Override
	public void printOptions() {
		super.printOptions();

		if(singleGwasFile != null){
			LOGGER.info(" * GWAS gene p-value, variant count, min variant p-value file: " + singleGwasFile.getPath());
		} else {
			LOGGER.info(" * GWAS gene p-value matrix path: " + gwasPvalueMatrixPath);
		}
		
		if (covariates != null) {
			LOGGER.info(" * covariates: " + covariates.getPath());
		}

		LOGGER.info(" * pathwayDatabases: ");
		for (PathwayDatabase curDb : pathwayDatabases) {
			LOGGER.info(" * - " + curDb.getName());
		}

		LOGGER.info(" * geneInfoFile: " + geneInfoFile.getPath());
		LOGGER.info(" * forceNormalGenePvalues: " + forceNormalGenePvalues);
		LOGGER.info(" * forceNormalGenePvalues: " + forceNormalPathwayPvalues);
		LOGGER.info(" * regressGeneLengths: " + regressGeneLengths);
		LOGGER.info(" * Exclude HLA during enrichment analysis: " + (excludeHla ? "on" : "off"));

	}

	public boolean isForceNormalGenePvalues() {
		return forceNormalGenePvalues;
	}

	public List<PathwayDatabase> getPathwayDatabases() {
		return pathwayDatabases;
	}

	public File getCovariates() {
		return covariates;
	}

	public boolean isForceNormalPathwayPvalues() {
		return forceNormalPathwayPvalues;
	}

	public boolean isRegressGeneLengths() {
		return regressGeneLengths;
	}

	public File getGeneInfoFile() {
		return geneInfoFile;
	}

	public File getSingleGwasFile() {
		return singleGwasFile;
	}

	public String getGwasPvalueMatrixPath() {
		return gwasPvalueMatrixPath;
	}
	
	public boolean isExcludeHla() {
		return excludeHla;
	}

	public boolean isSkipPvalueToZscore() {
		return skipPvalueToZscore;
	}
	
}
