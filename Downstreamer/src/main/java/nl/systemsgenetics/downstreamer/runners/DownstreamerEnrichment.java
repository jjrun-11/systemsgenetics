/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.systemsgenetics.downstreamer.runners;

import cern.colt.matrix.tdouble.DoubleMatrix1D;
import cern.colt.matrix.tdouble.DoubleMatrix2D;
import cern.jet.math.tdouble.DoubleFunctions;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import nl.systemsgenetics.downstreamer.DownstreamerStep2Results;
import nl.systemsgenetics.downstreamer.gene.Gene;
import nl.systemsgenetics.downstreamer.io.BlockPerFileDiagonalDoubleMatrixProvider;
import nl.systemsgenetics.downstreamer.io.IoUtils;
import nl.systemsgenetics.downstreamer.pathway.NaturalRankingTieFighter;
import nl.systemsgenetics.downstreamer.pathway.PathwayDatabase;
import nl.systemsgenetics.downstreamer.pathway.PathwayEnrichments;
import static nl.systemsgenetics.downstreamer.runners.DownstreamerRegressionEngine.blockDiagonalEigenDecomposition;
import static nl.systemsgenetics.downstreamer.runners.DownstreamerRegressionEngine.createBlockDiagonalIndexFromGenes;
import static nl.systemsgenetics.downstreamer.runners.DownstreamerRegressionEngine.createBlockDiagonalIndexFromGenes2;
import nl.systemsgenetics.downstreamer.runners.options.OptionsModeEnrichment;
import nl.systemsgenetics.downstreamer.summarystatistic.LinearRegressionResult;
import org.apache.commons.math3.stat.ranking.NaNStrategy;
import org.apache.commons.math3.stat.ranking.TiesStrategy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import umcg.genetica.math.matrix2.DoubleMatrixDataset;
import umcg.genetica.math.matrix2.DoubleMatrixDatasetFastSubsetLoader;
import umcg.genetica.math.stats.ZScores;

/**
 *
 * @author patri
 */
public class DownstreamerEnrichment {

	private static final Logger LOGGER = LogManager.getLogger(DownstreamerEnrichment.class);
	private static final String GENE_LENGTH_COL_NAME = "GeneLengths";

	public static DownstreamerStep2Results enrichmentAnalysis(OptionsModeEnrichment options) throws Exception {

		final List<PathwayDatabase> pathwayDatabases = options.getPathwayDatabases();

		//quick check if all pathway databases exist before loading all the other data.
		for (PathwayDatabase pd : pathwayDatabases) {
			if (!pd.exist()) {
				throw new FileNotFoundException("Could not read: " + pd.getLocation() + ".dat or .datg");
			}
		}

		// Load the genes to run the analysis on
		LinkedHashMap<String, Gene> genes = IoUtils.readGenesMap(options.getGeneInfoFile());
		LOGGER.info("Loaded " + genes.size() + " genes");

		final DoubleMatrixDataset<String, String> gwasGeneZscores;//might contain p-values when loading but is later inplace converted
		final DoubleMatrixDataset<String, String> gwasGeneVarCount;
		final DoubleMatrixDataset<String, String> gwasGeneMinVarPvalue;

		if (options.getSingleGwasFile() != null) {
			// Load GWAS data Cols: genes, pvalue, nSNPs, min SNP p-value
			DoubleMatrixDataset<String, String> gwasData = DoubleMatrixDataset.loadDoubleData(options.getSingleGwasFile().getAbsolutePath());

			ArrayList<String> colNames = gwasData.getColObjects();

			gwasGeneZscores = gwasData.viewColSelection(colNames.get(0));
			gwasGeneVarCount = gwasData.viewColSelection(colNames.get(1));
			gwasGeneMinVarPvalue = gwasData.viewColSelection(colNames.get(2));

		} else {

			gwasGeneZscores = DoubleMatrixDataset.loadDoubleData(options.getGwasPvalueMatrixPath() + "_pvalues");
			gwasGeneVarCount = DoubleMatrixDataset.loadDoubleData(options.getGwasPvalueMatrixPath() + "_nvar");
			gwasGeneMinVarPvalue = DoubleMatrixDataset.loadDoubleData(options.getGwasPvalueMatrixPath() + "_minVarPvalue");

		}

		//Inverse order of minimum variant p-value to later help with tie breaking
		gwasGeneMinVarPvalue.getMatrix().assign(DoubleFunctions.functions.neg);

		if (!options.isSkipPvalueToZscore()) {
			inplacePvalueToZscore(gwasGeneZscores);
		}

		final Set<String> hlaGenes;
		if (options.isExcludeHla()) {
			hlaGenes = new HashSet<>();
			for (Gene gene : genes.values()) {
				if (gene.overlaps(options.getHla())) {
					hlaGenes.add(gene.getGene());
				}
			}
			LOGGER.info("Excluding " + hlaGenes.size() + " genes");

		} else {
			hlaGenes = Collections.EMPTY_SET;
		}

		ArrayList<String> allGwasGenes = gwasGeneZscores.getRowObjects();

		LinkedHashSet<String> selectedGenes = new LinkedHashSet<>();

		//This loop will create a list of genes that should be selected
		genes:
		for (int g = 0; g < gwasGeneZscores.rows(); ++g) {

			String gene = allGwasGenes.get(g);

			if (hlaGenes.contains(gene)) {
				//is hla gene so don't add to selected
				continue;
			}

			if (gwasGeneVarCount.getElementQuick(g, 0) < 1) {
				//if no SNPs are found around gene don't use
				//Assuses this is equal for alle GWASes
				continue;
			}

			if (!genes.containsKey(gene)) {
				//if gene is not in list of genes to use, skip
				continue;
			}

			for (int c = 0; c < gwasGeneZscores.columns(); ++c) {
				if (Double.isNaN(gwasGeneZscores.getElementQuick(g, c))) {
					//gene did not have a valid p-value
					continue genes;
				}
			}

			selectedGenes.add(gene);

		}

		final double[] geneLengths = new double[selectedGenes.size()];
		int geneIndex = 0;
		// Ensures the geneLength vector is in the same order as the matrices
		for (String gene : selectedGenes) {
			Gene geneInfo = genes.get(gene);
			geneLengths[geneIndex] = Math.log(geneInfo.getLength()) / Math.log(10);
			geneIndex++;
		}

		// Load optinal covariates
		final DoubleMatrixDataset<String, String> covariatesToCorrectGenePvalues;
		if (options.getCovariates() != null) {
			DoubleMatrixDataset<String, String> covariatesToCorrectGenePvaluesTmp = DoubleMatrixDataset.loadDoubleData(options.getCovariates().getAbsolutePath());

			if (!covariatesToCorrectGenePvaluesTmp.getHashRows().keySet().containsAll(selectedGenes)) {
				throw new Exception("Not all genes are found in the covariate file");
			}

			if (options.isRegressGeneLengths()) {

				final ArrayList<String> allCovariates = new ArrayList<>(covariatesToCorrectGenePvaluesTmp.getHashCols().keySet());
				allCovariates.add(GENE_LENGTH_COL_NAME);

				covariatesToCorrectGenePvalues = new DoubleMatrixDataset<>(selectedGenes, allCovariates);

				covariatesToCorrectGenePvalues.viewColSelection(covariatesToCorrectGenePvaluesTmp.getHashCols().keySet()).getMatrix().assign(covariatesToCorrectGenePvaluesTmp.viewRowSelectionMatrix(selectedGenes));

				covariatesToCorrectGenePvalues.viewCol(GENE_LENGTH_COL_NAME).assign(geneLengths);

			} else {
				covariatesToCorrectGenePvalues = covariatesToCorrectGenePvaluesTmp.viewRowSelection(selectedGenes);
			}

		} else if (options.isRegressGeneLengths()) {
			//if no other covariates then create covariate matrix with only gene lengths
			final ArrayList<String> allCovariates = new ArrayList<>(1);
			allCovariates.add(GENE_LENGTH_COL_NAME);

			covariatesToCorrectGenePvalues = new DoubleMatrixDataset<>(selectedGenes, allCovariates);
		} else {
			covariatesToCorrectGenePvalues = null;
		}

		final Map<String, List<Gene>> chrArmGeneMap = IoUtils.readGenesAsChrArmMap(options.getGeneInfoFile());
		final BlockPerFileDiagonalDoubleMatrixProvider geneCorLoader = new BlockPerFileDiagonalDoubleMatrixProvider(options.getGeneGeneCorrelationPrefix(), "_correlations");

		final ArrayList<PathwayEnrichments> pathwayEnrichments = new ArrayList<>(pathwayDatabases.size());

		for (PathwayDatabase pathwayDatabase : pathwayDatabases) {

			final DoubleMatrixDatasetFastSubsetLoader pathwayMatrixLoader = new DoubleMatrixDatasetFastSubsetLoader(pathwayDatabase.getLocation());
			ArrayList<String> genesOverlappingWithPathwayDatabase = new ArrayList<>(selectedGenes.size());

			for (String pathwayGene : pathwayMatrixLoader.getAllRowIdentifiers()) {
				if (selectedGenes.contains(pathwayGene)) {
					genesOverlappingWithPathwayDatabase.add(pathwayGene);
				}
			}

			final DoubleMatrixDataset<String, String> covariatesToCorrectGenePvaluesSubset = covariatesToCorrectGenePvalues == null ? null : covariatesToCorrectGenePvalues.viewRowSelection(genesOverlappingWithPathwayDatabase);

			DoubleMatrixDataset<String, String> gwasGeneZscoreSubset;
			if (options.isForceNormalGenePvalues()) {
				gwasGeneZscoreSubset = createColumnForceNormalDuplicate(gwasGeneZscores.viewRowSelection(genesOverlappingWithPathwayDatabase), gwasGeneMinVarPvalue.viewRowSelection(genesOverlappingWithPathwayDatabase));
			} else {
				gwasGeneZscoreSubset = gwasGeneZscores.viewRowSelection(genesOverlappingWithPathwayDatabase).duplicate();
			}

			final DoubleMatrixDataset<String, String> pathwayData;

			{
				//first in tmp to remove empty pathways after selecting genes
				final DoubleMatrixDataset<String, String> pathwayDataTmp = pathwayMatrixLoader.loadSubsetOfRowsBinaryDoubleData(genesOverlappingWithPathwayDatabase);
				final List<String> allColumns = pathwayDataTmp.getColObjects();
				ArrayList<String> included = new ArrayList<>(allColumns.size());
				for (int col = 0; col < pathwayDataTmp.columns(); ++col) {
					if (pathwayDataTmp.getCol(col).cardinality() >= 10) {
						included.add(allColumns.get(col));
					}
				}

				if(pathwayDatabase.isEigenvectors()){
					pathwayDataTmp.normalizeColumns();
				}
				
				if (allColumns.size() == included.size()) {
					pathwayData = pathwayDataTmp;
				} else {
					pathwayData = pathwayDataTmp.viewColSelection(included);
					LOGGER.warn("Excluded features from " + pathwayDatabase.getName() + " because fewer than 10 non zero genes");
				}

			}

			LOGGER.info("Working on: " + pathwayDatabase.getName() + " with " + genesOverlappingWithPathwayDatabase.size() + " genes and " + pathwayData.columns() + " features.");

			if (options.isForceNormalPathwayPvalues()) {
				pathwayData.createColumnForceNormalInplace();
			}

			final List<int[]> blockDiagonalIndices = DownstreamerRegressionEngine.createBlockDiagonalIndexFromGenes(chrArmGeneMap, genesOverlappingWithPathwayDatabase);
			LinkedHashMap<String, ArrayList<String>> blockDiagonalIndicesForEigen = createBlockDiagonalIndexFromGenes2(chrArmGeneMap, genesOverlappingWithPathwayDatabase);

			//Do eigen decompose on the gene-gene correlation matrices
			//eigen[0] L = eigen values
			//eigen[1] U = eigen vectors
			DoubleMatrixDataset<String, String>[] eigen = blockDiagonalEigenDecomposition(genesOverlappingWithPathwayDatabase, geneCorLoader, blockDiagonalIndicesForEigen, options.isJblas());

			//list contains traits
			List<LinearRegressionResult> pathwayRegeressionResults = DownstreamerRegressionEngine.performDownstreamerRegression(
					pathwayData,
					gwasGeneZscoreSubset,
					covariatesToCorrectGenePvaluesSubset,
					eigen[1], eigen[0], blockDiagonalIndices, true, covariatesToCorrectGenePvalues != null, options.isJblas());

			final DoubleMatrixDataset<String, String> pathwayPvalues;
			final DoubleMatrixDataset<String, String> pathwayQvalues;
			final DoubleMatrixDataset<String, String> pathwayBetas;

			if (pathwayDatabase.isEigenvectors()) {
				//Enrichment on eigenvectors with intent to gene reconstruction using significant eigen vectors
				//Note these are not the eigen vectors of the gene-gene correlations but of the co-expression data

				pathwayPvalues = new DoubleMatrixDataset<>(pathwayData.getRowObjects(), gwasGeneZscores.getColObjects());
				pathwayQvalues = new DoubleMatrixDataset<>(pathwayData.getRowObjects(), gwasGeneZscores.getColObjects());
				pathwayBetas = new DoubleMatrixDataset<>(pathwayData.getRowObjects(), gwasGeneZscores.getColObjects());

				final DoubleMatrixDataset<String, String> pathwayPvaluesIntermediates = new DoubleMatrixDataset<>(pathwayData.getColObjects(), gwasGeneZscores.getColObjects());
				final DoubleMatrixDataset<String, String> pathwaySeIntermediates = new DoubleMatrixDataset<>(pathwayData.getColObjects(), gwasGeneZscores.getColObjects());
				final DoubleMatrixDataset<String, String> pathwayBetasIntermediates = new DoubleMatrixDataset<>(pathwayData.getColObjects(), gwasGeneZscores.getColObjects());
				final DoubleMatrixDataset<String, String> pathwayTstatsIntermediates = new DoubleMatrixDataset<>(pathwayData.getColObjects(), gwasGeneZscores.getColObjects());
				

				//columns in gwasGeneZscores are traits
				for (int trait = 0; trait < gwasGeneZscores.columns(); ++trait) {
					LinearRegressionResult thisGwasRestuls = pathwayRegeressionResults.get(trait);
					System.out.println("Degree freedom: " + thisGwasRestuls.getDegreesOfFreedom());
					System.out.println("lin reg res: " + thisGwasRestuls.getName() + " " + thisGwasRestuls.getBeta().getMatrix().toStringShort());
					pathwayPvaluesIntermediates.getCol(trait).assign(thisGwasRestuls.getPvalueForMainEffect());
					pathwaySeIntermediates.getCol(trait).assign(thisGwasRestuls.getSeForMainEffect());
					pathwayBetasIntermediates.getCol(trait).assign(thisGwasRestuls.getBetaForMainEffect());
					pathwayTstatsIntermediates.getCol(trait).assign(thisGwasRestuls.getTstatForMainEffect());
				}

				final DoubleMatrixDataset<String, String> pathwayQvaluesIntermediates = DownstreamerUtilities.adjustPvaluesBenjaminiHochberg(pathwayPvaluesIntermediates);

				System.out.println("pathwayQvaluesIntermediates: " + pathwayQvaluesIntermediates.getMatrix().toStringShort());

				final ArrayList<String> eigenvectorsInDataset = pathwayPvaluesIntermediates.getRowObjects();

				System.out.println("eigenvectorsInDataset: " + eigenvectorsInDataset.size());
				

				final int numberEigenvectors = eigenvectorsInDataset.size();

				for (int trait = 0; trait < gwasGeneZscores.columns(); ++trait) {

					final ArrayList<String> significantEigenvectorsIdThisTrait = new ArrayList<>();

					for (int eigenVectorI = 0; eigenVectorI < numberEigenvectors; ++eigenVectorI) {

						if (pathwayQvaluesIntermediates.getElementQuick(trait, eigenVectorI) <= 0.05) {
							significantEigenvectorsIdThisTrait.add(eigenvectorsInDataset.get(eigenVectorI));
						}

					}

					System.out.println("significantEigenvectorsIdThisTrait: " + significantEigenvectorsIdThisTrait.size());
					

					if (significantEigenvectorsIdThisTrait.size() > 0) {

						final DoubleMatrix2D significantEigenvectorsThisTrait = pathwayData.viewColSelection(significantEigenvectorsIdThisTrait).getMatrix();

						final DoubleMatrix1D traitSignificantBetas = pathwayBetasIntermediates.viewRowSelection(significantEigenvectorsIdThisTrait).getCol(trait);
						final DoubleMatrix1D traitSignificantTstats = pathwayTstatsIntermediates.viewRowSelection(significantEigenvectorsIdThisTrait).getCol(trait);

						final DoubleMatrix1D reconstructedScore = traitSignificantBetas.like();
						final DoubleMatrix1D reconstructedScore2 = traitSignificantBetas.like();

						significantEigenvectorsThisTrait.viewDice().zMult(traitSignificantBetas, reconstructedScore);
						significantEigenvectorsThisTrait.viewDice().zMult(traitSignificantTstats, reconstructedScore2);

						pathwayPvalues.getCol(trait).assign(reconstructedScore);
						pathwayBetas.getCol(trait).assign(reconstructedScore2);
						pathwayQvalues.getCol(trait).assign(reconstructedScore);
					}
				}

				pathwayPvalues.save(options.getOutputBasePath() + "_" + pathwayDatabase.getName() + "_" + "reconstructedScoresBeta.txt");
				pathwayBetas.save(options.getOutputBasePath() + "_" + pathwayDatabase.getName() + "_" + "reconstructedScoresTstat.txt");
				pathwayPvaluesIntermediates.save(options.getOutputBasePath() + "_" + pathwayDatabase.getName() + "_" + "eigenvectorPvalues.txt");
				pathwaySeIntermediates.save(options.getOutputBasePath() + "_" + pathwayDatabase.getName() + "_" + "eigenvectorSes.txt");
				pathwayBetasIntermediates.save(options.getOutputBasePath() + "_" + pathwayDatabase.getName() + "_" + "eigenvectorBetas.txt");
				pathwayTstatsIntermediates.save(options.getOutputBasePath() + "_" + pathwayDatabase.getName() + "_" + "eigenvectorTstats.txt");
				
				
			} else {
				//normal pathway enrichment just get regression p-values

				pathwayPvalues = new DoubleMatrixDataset<>(pathwayData.getColObjects(), gwasGeneZscores.getColObjects());
				pathwayBetas = new DoubleMatrixDataset<>(pathwayData.getColObjects(), gwasGeneZscores.getColObjects());

				//columns in gwasGeneZscores are traits
				for (int trait = 0; trait < gwasGeneZscores.columns(); ++trait) {
					LinearRegressionResult thisGwasRestuls = pathwayRegeressionResults.get(trait);
					pathwayPvalues.getCol(trait).assign(thisGwasRestuls.getPvalueForMainEffect());
					pathwayBetas.getCol(trait).assign(thisGwasRestuls.getBetaForMainEffect());
				}

				pathwayQvalues = DownstreamerUtilities.adjustPvaluesBenjaminiHochberg(pathwayPvalues);
			}

			pathwayEnrichments.add(new PathwayEnrichments(pathwayDatabase, pathwayBetas, pathwayPvalues, pathwayQvalues));

		}

		return new DownstreamerStep2Results(pathwayEnrichments, gwasGeneZscores);

	}

	private static void inplacePvalueToZscore(DoubleMatrixDataset dataset) {
		final DoubleMatrix2D matrix = dataset.getMatrix();

		// Inplace convert gene p-values to z-scores
		IntStream.range(0, matrix.rows()).parallel().forEach(r -> {
			for (int c = 0; c < matrix.columns(); ++c) {
				matrix.setQuick(r, c, -ZScores.pToZTwoTailed(matrix.getQuick(r, c)));
			}
		});
	}

	public static DoubleMatrixDataset<String, String> createColumnForceNormalDuplicate(DoubleMatrixDataset<String, String> matrix, DoubleMatrixDataset<String, String> tieBreaker) {

		DoubleMatrixDataset<String, String> newDataset = new DoubleMatrixDataset<>(matrix.getHashRows(), matrix.getHashCols());

		NaturalRankingTieFighter ranking = new NaturalRankingTieFighter(NaNStrategy.FAILED,
				TiesStrategy.AVERAGE);

		IntStream.range(0, matrix.columns()).parallel().forEach(c -> {

			double[] col = matrix.getCol(c).toArray();
			double[] colTie = tieBreaker.getCol(c).toArray();

			double mean = JSci.maths.ArrayMath.mean(col);
			double stdev = JSci.maths.ArrayMath.standardDeviation(col);

			double[] rankedValues = ranking.rank(col, colTie);

			for (int s = 0; s < matrix.rows(); s++) {
				double pValue = (0.5d + rankedValues[s] - 1d) / (double) (rankedValues.length);

				newDataset.setElementQuick(s, c, mean + cern.jet.stat.Probability.normalInverse(pValue) * stdev);
			}

		});

		return newDataset;

	}

}
