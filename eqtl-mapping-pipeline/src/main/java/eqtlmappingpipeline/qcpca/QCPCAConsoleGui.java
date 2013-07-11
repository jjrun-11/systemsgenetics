/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eqtlmappingpipeline.qcpca;

import umcg.genetica.console.ConsoleGUIElems;

/**
 *
 * @author harmjan
 */
public class QCPCAConsoleGui {
    
    public QCPCAConsoleGui(String[] args){ 
        
        String settingsfile = null;
        String settingstexttoreplace = null;
        String settingstexttoreplacewith = null;
        String in = null;
        String out = null;
        boolean cis = false;
        boolean trans = false;
        int perm = 1;
        String outtype = "text";
        String inexp = null;
        String inexpplatform = null;
        String inexpannot = null;
        String gte = null;
        String snpfile = null;
        Integer threads = null;
        
        boolean performEigenvectorQTLMapping = false;
        boolean inventorize      = false;
        boolean inventorizepcqtl = false;
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String val = null;

            if (i + 1 < args.length) {
                val = args[i + 1];
            }

            if (arg.equals("--settings")) {
                settingsfile = val;
            } else if (arg.equals("--replacetext")) {
                settingstexttoreplace = val;
            } else if (arg.equals("--replacetextwith")) {
                settingstexttoreplacewith = val;
            } else if (arg.equals("--in")) {
                in = val;
            } else if (arg.equals("--out")) {
                out = val;
            } else if (arg.equals("--inexp")) {
                inexp = val;
            } else if (arg.equals("--inexpplatform")) {
                inexpplatform = val;
            } else if (arg.equals("--inexpannot")) {
                inexpannot = val;
            } else if (arg.equals("--gte")) {
                gte = val;
            } else if (arg.equals("--pcqtl")) {
                performEigenvectorQTLMapping = true;
            } else if (arg.equals("--inventorize")) {
                inventorize = true;
            } else if (arg.equals("--snps")) {
                snpfile = val;
            } else if (arg.equals("--inventorize-pcqtl")) {
                inventorize = true;
                inventorizepcqtl = true;
            } else if (arg.equals("--threads")) {
                try{
                    threads = Integer.parseInt(val);
                } catch (NumberFormatException e){
                    System.err.println("Error --threads should be an integer");
                }
                        
            }
        }

        try {
            if(in == null || inexp == null || out == null){
                System.out.println("ERROR: Please supply --in, --inexp and --out");
                printUsage();
            } else {
                QCPCA q = new QCPCA();
                q.run(inexp, inexpplatform, in, gte, inexpannot, out, snpfile);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
    
    private void printUsage() {
        System.out.print("\tQCPCA\n"+ConsoleGUIElems.LINE);
        System.out.println("QCPCA performs principal component analysis on the genotype and gene expression data in order to detect for example population stratification effects.");
        
        System.out.print("\nExamples\n"+ConsoleGUIElems.LINE);
        System.out.println("Example using commandline:\tjava -jar eQTLMappingPipeline.jar --mode qcpca --in /path/to/GenotypeMatrix.dat/ --out /path/to/output/ --inexp /path/to/expressiondata.txt --inexpannot /path/to/annotation.txt --gte /path/to/genotypetoexpressioncoupling.txt");
        
        System.out.println("");
        System.out.print("Command line options:\n"+ConsoleGUIElems.LINE);
        System.out.println("--in\t\t\tdir\t\tLocation of the genotype data\n"
                + "--out\t\t\tdir\t\tLocation where the output should be stored\n"
                + "--inexp\t\t\tstring\t\tLocation of expression data\n"
                + "--inexpplatform\t\tstring\t\tGene expression platform\n"
                + "--inexpannot\t\tstring\t\tLocation of annotation file for gene expression data\n"
                + "--gte\t\t\tstring\t\tLocation of genotype to expression coupling file\n"
                + "--snps\t\t\tstring\t\tDon't prune SNPs, but use list of pruned SNPs (eg. generated by PLINK)"
                );
        System.out.println("");
    }
    
}
