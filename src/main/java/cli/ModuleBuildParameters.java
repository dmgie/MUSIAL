package cli;

import com.google.gson.internal.LinkedTreeMap;
import components.IO;
import components.Logging;
import components.Validation;
import datastructure.FeatureEntry;
import datastructure.SampleEntry;
import exceptions.MusialException;
import org.apache.commons.io.FilenameUtils;
import org.biojava.nbio.genome.parsers.gff.FeatureI;
import org.biojava.nbio.genome.parsers.gff.FeatureList;
import org.biojava.nbio.genome.parsers.gff.Location;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parses command line interface arguments for the `MUSIAL BUILD` module.
 * <p>
 * Used to parse and validate passed command line options or print help information to the user. Parsed arguments are
 * stored to be accessible for other components of the tool.
 *
 * @author Simon Hackl
 * @version 2.1
 * @since 2.1
 */
public final class ModuleBuildParameters {
    /**
     * Minimum (read) coverage in order to call an SNV.
     */
    public Double minCoverage;
    /**
     * Minimum allele frequency (with respect to reads) in order to call an SNV.
     */
    public Double minHomFrequency;
    /**
     * Minimum allele frequency (with respect to reads) in order to call heterozygous SNVs.
     */
    public Double minHetFrequency;
    /**
     * Maximum allele frequency (with respect to reads) in order to call heterozygous SNVs.
     */
    public Double maxHetFrequency;
    /**
     * Minimum quality, i.e. the value given in the QUAL field of .vcf files, in order to call an SNV.
     */
    public Double minQuality;
    /**
     * {@link File} object specifying the reference genome sequence .fasta file.
     */
    public File referenceFASTA;
    /**
     * {@link File} object specifying the reference genome annotation .gff file.
     */
    public File referenceGFF;
    /**
     * {@link ConcurrentSkipListMap} of {@link String}/{@link SampleEntry} pairs.
     */
    public final ConcurrentSkipListMap<String, SampleEntry> samples = new ConcurrentSkipListMap<>();
    /**
     * {@link ConcurrentSkipListMap} of {@link String}/{@link FeatureEntry} pairs.
     */
    public final ConcurrentSkipListMap<String, FeatureEntry> features = new ConcurrentSkipListMap<>();
    /**
     * {@link Boolean} whether to analyze the whole reference genome independent of features.
     */
    public boolean genomeAnalysis;
    /**
     * {@link File} object specifying the output file.
     */
    public File outputFile;
    /**
     * {@link FeatureList} object specifying parsed genome features.
     */
    private FeatureList referenceFeatures = null;
    /**
     * Optional positions to exclude from the analysis.
     */
    public final HashMap<String, TreeSet<Integer>> excludedPositions = new HashMap<>();
    /**
     * Prefix to use for exception messages.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final String EXCEPTION_PREFIX = "(Module BUILD Configuration)";

    /**
     * Constructor of the {@link ModuleBuildParameters} class.
     * <p>
     * Used to parse and validate command line interface input. Parsed arguments are stored - and accessible by other
     * components - via class properties.
     *
     * @param parameters {@link LinkedTreeMap} containing parameter information.
     * @throws MusialException If IO file validation fails; If CLI parameter validation fails.
     */
    public ModuleBuildParameters(LinkedTreeMap<Object, Object> parameters)
            throws MusialException {
        // Parse minCoverage
        if (parameters.containsKey("minCoverage")
                && Validation.isPositiveDouble(String.valueOf(parameters.get("minCoverage")))) {
            this.minCoverage = (Double) parameters.get("minCoverage");
        } else {
            throw new MusialException(EXCEPTION_PREFIX + " Invalid or missing " + Logging.colorParameter("minCoverage") + "; expected positive float.");
        }
        // Parse minFrequency
        if (parameters.containsKey("minHomFrequency")
                && Validation.isPercentage(String.valueOf(parameters.get("minHomFrequency")))) {
            this.minHomFrequency = (Double) parameters.get("minHomFrequency");
        } else {
            throw new MusialException(EXCEPTION_PREFIX + " Invalid or missing " + Logging.colorParameter("minHomFrequency") + "; expected float between 0.0 and 1.0.");
        }
        // Parse minHetFrequency
        if (parameters.containsKey("minHetFrequency")
                && Validation.isPercentage(String.valueOf(parameters.get("minHetFrequency")))) {
            this.minHetFrequency = (Double) parameters.get("minHetFrequency");
        } else {
            throw new MusialException(EXCEPTION_PREFIX + " Invalid or missing " + Logging.colorParameter("minHetFrequency") + "; expected float between 0.0 and 1.0.");
        }
        // Parse maxHetFrequency
        if (parameters.containsKey("maxHetFrequency")
                && Validation.isPercentage(String.valueOf(parameters.get("maxHetFrequency")))) {
            this.maxHetFrequency = (Double) parameters.get("maxHetFrequency");
        } else {
            throw new MusialException(EXCEPTION_PREFIX + " Invalid or missing " + Logging.colorParameter("maxHetFrequency") + "; expected float between 0.0 and 1.0.");
        }
        // Parse minQuality
        if (parameters.containsKey("minQuality")
                && Validation.isPositiveDouble(String.valueOf(parameters.get("minQuality")))) {
            this.minQuality = (Double) parameters.get("minQuality");
        } else {
            throw new MusialException(EXCEPTION_PREFIX + " Invalid or missing " + Logging.colorParameter("minQuality") + "; expected positive float.");
        }
        // Parse reference genome (.fasta)
        if (!parameters.containsKey("referenceFASTA")) {
            throw new MusialException(EXCEPTION_PREFIX + " Missing " + Logging.colorParameter("referenceFASTA") + "; expected path to file.");
        }
        File r = new File((String) parameters.get("referenceFASTA"));
        if (Validation.isFile(r)) {
            this.referenceFASTA = r;
        } else {
            throw new MusialException(EXCEPTION_PREFIX + " Invalid " + Logging.colorParameter("referenceFASTA") + "; failed to read file.");
        }
        // Parse reference annotation (.gff/.gff3)
        if (!parameters.containsKey("referenceGFF")) {
            throw new MusialException(EXCEPTION_PREFIX + " Missing " + Logging.colorParameter("referenceGFF") + "; expected path to file.");
        }
        File a = new File((String) parameters.get("referenceGFF"));
        if (Validation.isFile(a)) {
            this.referenceGFF = a;
        } else {
            throw new MusialException(EXCEPTION_PREFIX + " Invalid " + Logging.colorParameter("referenceGFF") + "; failed to read file.");
        }
        // Parse output file
        if (!parameters.containsKey("outputFile")) {
            throw new MusialException(EXCEPTION_PREFIX + " Missing " + Logging.colorParameter("outputFile") + "; expected path to file.");
        }
        File o = new File((String) parameters.get("outputFile"));
        if (Validation.isDirectory(new File(o.getParent()))) {
            this.outputFile = o;
        } else {
            throw new MusialException(EXCEPTION_PREFIX + " Invalid " + Logging.colorParameter("outputFile") + " " + Logging.colorParameter((String) parameters.get("outputFile")) + "; unable to access directory.");
        }
        if (Validation.isFile(this.outputFile)) {
            throw new MusialException(EXCEPTION_PREFIX + " Specified " + Logging.colorParameter("outputFile") + " " + Logging.colorParameter((String) parameters.get("outputFile")) + " already exists.");
        }
        // Parse whether to run whole genome analysis.
        this.genomeAnalysis = parameters.containsKey("genomeAnalysis") && (Boolean) parameters.get("genomeAnalysis");
        // Parse excluded positions
        if (parameters.containsKey("excludedPositions")) {
            //noinspection rawtypes,unchecked
            LinkedTreeMap<String, ArrayList<Double>> excludedPositions = (LinkedTreeMap) parameters.get("excludedPositions");
            for (String key : excludedPositions.keySet()) {
                this.excludedPositions.put(key, new TreeSet<>());
                for (Double excludedPosition : excludedPositions.get(key)) {
                    this.excludedPositions.get(key).add(excludedPosition.intValue());
                }
            }
        }
        // Parse samples
        @SuppressWarnings("rawtypes")
        LinkedTreeMap sampleEntry;
        // Check if any samples can be parsed from a passed samplesDir key in the configuration.
        if (parameters.containsKey("samplesDir")) {
            HashSet<String> samplesFromDirectory;
            try (Stream<Path> stream = Files.list(Paths.get((String) parameters.get("samplesDir")))) {
                samplesFromDirectory = (HashSet<String>) stream
                        .filter(file -> !Files.isDirectory(file))
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .collect(Collectors.toSet());
            } catch (IOException e) {
                throw new MusialException(EXCEPTION_PREFIX + " Unable to access path specified at " + Logging.colorParameter("samplesDir") + ".");
            }
            for (String sampleFileFromDirectory : samplesFromDirectory) {
                if ( sampleFileFromDirectory.endsWith(".vcf") ) {
                    //noinspection rawtypes
                    sampleEntry = new LinkedTreeMap();
                    String sampleName = FilenameUtils.removeExtension(sampleFileFromDirectory);
                    File sampleVcfFile = new File(parameters.get("samplesDir") + sampleFileFromDirectory);
                    if (sampleEntry.get("vcfFile") != null && !Validation.isFile(sampleVcfFile)) {
                        throw new MusialException(EXCEPTION_PREFIX + " Failed to access specified `vcf` file for sample " + Logging.colorParameter(sampleFileFromDirectory));
                    }
                    addSample(
                            sampleName,
                            sampleVcfFile,
                            new HashMap<>()
                    );
                }
            }
        }
        // Check if any samples can be parsed explicitly from the samples key in the configuration.
        if (parameters.containsKey("samples")) {
            @SuppressWarnings("rawtypes")
            LinkedTreeMap samples = (LinkedTreeMap) parameters.get("samples");
            for (Object sampleKey : samples.keySet()) {
                //noinspection rawtypes
                sampleEntry = (LinkedTreeMap) samples.get(sampleKey);
                File sampleVcfFile = new File((String) sampleEntry.get("vcfFile"));
                if (sampleEntry.get("vcfFile") != null && !Validation.isFile(sampleVcfFile)) {
                    throw new MusialException(EXCEPTION_PREFIX + " Failed to access specified `vcf` file for sample " + Logging.colorParameter((String) sampleKey));
                }
                Map<String, String> annotations;
                if (sampleEntry.containsKey("annotations")) {
                    //noinspection unchecked
                    annotations = (Map<String, String>) sampleEntry.get("annotations");
                } else {
                    annotations = new HashMap<>();
                }
                addSample(
                        (String) sampleKey,
                        sampleVcfFile,
                        annotations
                );
            }
        }
        if ((!parameters.containsKey("samples") && !parameters.containsKey("samplesDir")) || this.samples.keySet().size() == 0) {
            throw new MusialException(EXCEPTION_PREFIX + " Missing " + Logging.colorParameter("samples") + " or " + Logging.colorParameter("samplesDir") + " parameter or failed to parse any samples; expected at least one entry.");
        }
        // Parse features
        if (!parameters.containsKey("features")) {
            throw new MusialException(EXCEPTION_PREFIX + " Missing " + Logging.colorParameter("features") + "; expected at least one entry.");
        }
        //noinspection rawtypes
        LinkedTreeMap features = (LinkedTreeMap) parameters.get("features");
        if (features.keySet().size() == 0 && !this.genomeAnalysis) {
            throw new MusialException(EXCEPTION_PREFIX + " Missing " + Logging.colorParameter("features") + "; expected at least one entry.");
        }
        //noinspection rawtypes
        LinkedTreeMap featureEntry;
        for (Object featureKey : features.keySet()) {
            //noinspection rawtypes
            featureEntry = (LinkedTreeMap) features.get(featureKey);
            File featurePdbFile;
            boolean isCds = false;
            if (featureEntry.containsKey("pdbFile")) {
                featurePdbFile = new File((String) featureEntry.get("pdbFile"));
                if (featureEntry.get("pdbFile") != null && !Validation.isFile(featurePdbFile)) {
                    throw new MusialException(EXCEPTION_PREFIX + " Failed to access specified `pdb` file for feature " + Logging.colorParameter((String) featureKey));
                }
                isCds = true;
            } else {
                featurePdbFile = null;
                if (featureEntry.containsKey("isCodingSequence")) {
                    isCds = ((Boolean) featureEntry.get("isCodingSequence"));
                }
            }
            //noinspection unchecked
            String matchKey = (String) featureEntry
                    .keySet()
                    .stream()
                    .filter(key -> key.toString().startsWith("MATCH_"))
                    .findFirst()
                    .orElse(null);
            if (matchKey == null || featureEntry.get(matchKey) == null) {
                throw new MusialException(EXCEPTION_PREFIX + " Failed to find `gff` attribute key/value pair to match feature " + Logging.colorParameter((String) featureKey));
            }
            Map<String, String> annotations;
            if (featureEntry.containsKey("annotations")) {
                //noinspection unchecked
                annotations = (Map<String, String>) featureEntry.get("annotations");
            } else {
                annotations = new HashMap<>();
            }
            addFeature(
                    (String) featureKey,
                    matchKey.replace("MATCH_", ""),
                    (String) featureEntry.get(matchKey),
                    featurePdbFile,
                    isCds,
                    annotations
            );
        }
    }

    /**
     * Initializes a {@link SampleEntry} object with the specified parameters and adds it to the samples list.
     *
     * @param name        {@link String}; The internal name to use for the sample.
     * @param vcfFile     {@link File} object pointing to a .vcf format file.
     * @param annotations {@link java.util.HashMap} of {@link String} key/pair values; sample meta information.
     * @throws MusialException If the specified .vcf file does not exist or can not be accessed.
     */
    private void addSample(String name, File vcfFile, Map<String, String> annotations) throws MusialException {
        if (vcfFile.isFile()) {
            SampleEntry sampleEntry = new SampleEntry(vcfFile, name);
            sampleEntry.annotations.putAll(annotations);
            this.samples.put(name, sampleEntry);
        } else {
            throw new MusialException(EXCEPTION_PREFIX + " Failed to access `vcf` file for sample " + Logging.colorParameter(name) + ":\t" + Logging.colorParameter(vcfFile.getAbsolutePath()));
        }
    }

    /**
     * Initializes a {@link FeatureEntry} object with the specified parameters and adds it to the features list.
     *
     * @param name        {@link String}; The internal name to use for the feature.
     * @param matchKey    {@link String}; The key of the attribute in the specified .gff format reference annotation to match the feature from.
     * @param matchValue  {@link String}; The value of the attribute in the specified .gff format reference annotation to match the feature from.
     * @param asCds       {@link Boolean}; Whether to consider feature as cds, independent of provided structure.
     * @param pdbFile     {@link File}; Optional object pointing to a .pdb format file yielding a protein structure derived for the (gene) feature.
     * @param annotations {@link java.util.HashMap} of {@link String} key/pair values; feature meta information.
     * @throws MusialException If the initialization of the {@link FeatureEntry} fails; If the specified .gff reference annotation or .pdb protein file can not be read; If the specified feature is not found or parsed multiple times from the reference annotation.
     */
    private void addFeature(String name, String matchKey, String matchValue, File pdbFile, boolean asCds, Map<String, String> annotations)
            throws MusialException {
        if (this.referenceFeatures == null) {
            try {
                this.referenceFeatures = IO.readGFF(this.referenceGFF);
            } catch (IOException e) {
                throw new MusialException(EXCEPTION_PREFIX + " Failed to read specified `gff` " + Logging.colorParameter(this.referenceGFF.getAbsolutePath()) + ":\t" +
                        e.getMessage());
            }
        }
        FeatureList matchedFeatures = this.referenceFeatures.selectByAttribute(matchKey, matchValue);
        if (matchedFeatures.size() == 0) {
            throw new MusialException(EXCEPTION_PREFIX + " Failed to match feature " + Logging.colorParameter(name) + " with attribute pair " +
                    Logging.colorParameter(matchKey + "=" + matchValue));
        } else if (matchedFeatures.size() > 1) {
            throw new MusialException(EXCEPTION_PREFIX + " Feature " + Logging.colorParameter(name) + " was matched multiple times ");
        } else {
            FeatureI matchedFeature = matchedFeatures.get(0);
            Location featureCoordinates = matchedFeature.location();
            String featureParentSequence = matchedFeature.seqname();
          /* FIXME: Starting positions are shifted by minus one, i.e. the returned values do not match with the ones of
              the `gff` files. This is currently fixed in the FeatureEntry.java class.
           */
            FeatureEntry featureEntry = new FeatureEntry(name, featureParentSequence, featureCoordinates.getBegin(),
                    featureCoordinates.getEnd(), asCds);
            featureEntry.annotations.putAll(annotations);
            this.features.put(name, featureEntry);
        }
        if (pdbFile != null && pdbFile.exists()) {
            if (Validation.isFile(pdbFile)) {
                this.features.get(name).pdbFile = pdbFile;
            } else {
                throw new MusialException(EXCEPTION_PREFIX + " Failed to access `pdb` file for feature " + Logging.colorParameter(name) + ":\t" + Logging.colorParameter(pdbFile.getAbsolutePath()));
            }
        }
    }

}