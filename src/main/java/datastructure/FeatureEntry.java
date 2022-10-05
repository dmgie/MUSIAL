package datastructure;

import components.Bio;
import components.IO;
import components.Logging;
import exceptions.MusialBioException;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.stat.inference.KolmogorovSmirnovTest;
import org.apache.commons.math3.stat.inference.TestUtils;
import org.biojava.nbio.structure.Chain;
import org.biojava.nbio.structure.Group;
import org.biojava.nbio.structure.Structure;
import org.javatuples.Triplet;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

/**
 * Internal representation of a reference sequence location that is subject to analysis. May represent the full
 * genome, a single gene, contigs or plasmids and chromosomes.
 * <p>
 * Pairs of instances of this class and {@link SampleEntry} instances are used internally as so called 'Run
 * entries' to specify a set of analysis tasks, i.e. which sample is analyzed with respect to which specified
 * reference feature.
 *
 * @author Simon Hackl
 * @version 2.1
 * @since 2.0
 */
public final class FeatureEntry {

    /**
     * The internal name of the entry.
     */
    public final String name;
    /**
     * The reference DNA sequence of the entry.
     */
    public String nucleotideSequence;
    /**
     * The translated reference DNA sequence of the entry.
     */
    public String translatedNucleotideSequence;
    /**
     * The allocated protein's aminoacid sequences, if this feature is a coding sequence.
     * <p>
     * The chain identifiers of the specified .pdb format file are used as keys.
     */
    public HashMap<String, String> proteinSequences;
    /**
     * The protein's structure in .pdb format, if this feature is a coding sequence.
     */
    public String structure;
    /**
     * The location of the entry on the reference, i.e. for genes the contig or chromosome the feature is located on.
     */
    public final String chromosome;
    /**
     * The 1-based indexed starting position of the feature.
     */
    public final int start;
    /**
     * The 1-based indexed end position of the feature.
     */
    public final int end;
    /**
     * Indicates if the feature is located on the sense strand.
     */
    public final boolean isSense;
    /**
     * Indicates if the feature represents a coding sequence.
     */
    public boolean isCodingSequence;
    /**
     * {@link HashMap} storing all {@link GenotypeEntry} instances, i.e. genotypes, associated with this feature.
     */
    public final HashMap<String, GenotypeEntry> genotypes = new HashMap<>();
    /**
     * {@link HashMap} storing all {@link ProteoformEntry} instances, i.e. proteoforms, associated with this feature.
     */
    public final HashMap<String, ProteoformEntry> proteoforms = new HashMap<>();
    /**
     * A {@link File} object pointing to a .pdb format file containing the protein structure information of this feature.
     */
    public transient File pdbFile;
    /**
     * A {@link HashMap} yielding any meta-information {@link String} key-value pairs about this {@link FeatureEntry}.
     */
    public final HashMap<String, String> annotations = new HashMap<>();
    /**
     * Hierarchical map structure to store variants wrt. genotypes. The first layer represents the
     * position on the feature. The second layer represents the variant content.
     */
    public final ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<String, NucleotideVariantEntry>>
            nucleotideVariants =
            new ConcurrentSkipListMap<>(Integer::compare);
    /**
     * Hierarchical map structure to store variants wrt. proteoforms. The first layer represents the
     * position on the feature. The second layer represents the variant content.
     * <p>
     * Stored positions have to be formatted as X+Y; X is the position on the reference, Y is the possibly
     * inserted position.
     */
    public final ConcurrentSkipListMap<String, ConcurrentSkipListMap<String, AminoacidVariantEntry>> aminoacidVariants =
            new ConcurrentSkipListMap<>((k1, k2) -> {
                int p1 = Integer.parseInt(k1.split("\\+")[0]);
                int p2 = Integer.parseInt(k2.split("\\+")[0]);
                if (p1 == p2) {
                    return Integer.compare(
                            k1.contains("+") ? k1.split("\\+")[1].hashCode() : 0,
                            k2.contains("+") ? k2.split("\\+")[1].hashCode() : 0
                    );
                }
                return Integer.compare(p1, p2);
            });

    /**
     * Constructor of {@link FeatureEntry}.
     *
     * @param entryName     {@link String} representing the internal name of the reference feature to analyze.
     * @param entryLocation {@link String} the name of the reference location (contig, chromosome, plasmid) the feature
     *                      is located on.
     * @param entryStart    {@link Integer} The 1-based indexed starting position of the feature on the reference.
     * @param entryEnd      {@link Integer} The 1-based indexed end position of the feature on the reference.
     * @throws MusialBioException If the specified locus is ambiguous.
     */
    public FeatureEntry(String entryName, String entryLocation, int entryStart, int entryEnd) throws MusialBioException {
        this.name = entryName;
        this.chromosome = entryLocation;
        this.isCodingSequence = false;
        if (entryStart >= 0 && entryEnd > 0 && (entryEnd > entryStart)) {
            // CASE: Feature is on sense strand.
            this.isSense = true;
            this.start = entryStart + 1;
            this.end = entryEnd;
        } else if (entryStart < 0 && entryEnd <= 0 && (entryEnd < entryStart)) {
            // CASE: Feature is on anti-sense strand.
            this.isSense = false;
            this.start = -entryStart + 1;
            this.end = -entryEnd;
        } else {
            throw new MusialBioException(
                    "Failed to add gene feature due to faulty position data (start, end)\t" + entryStart + ", " + entryEnd);
        }
    }

    /**
     * Returns a {@link String} that is used as internal genotype or proteoform name.
     * <p>
     * These names are generated by concatenating the prefix 'GT' or 'PF' with the HashCode of the {@link GenotypeEntry}
     * and {@link ProteoformEntry} mandatory 'VARIANTS' annotation value converted to a {@link String} for which a
     * negative sign is converted to a one and which in total was padded with prepending 0s to a length of 10.
     *
     * @param concatVariants {@link String} representation of the variants of one genotype or proteoform in the format <ALT>#<POS>.
     * @return {@link String} intended to be used as internal genotype or proteoform name.
     */
    public static String generateEntryName(String concatVariants, String prefix) {
        assert !Objects.equals(concatVariants, "");
        assert prefix.equals("GT") || prefix.equals("PF");
        StringBuilder nameBuilder = new StringBuilder();
        nameBuilder.append(prefix);
        String variantsHashCode = String.valueOf(new HashSet<>(List.of(concatVariants.split("\\."))));
        if (variantsHashCode.startsWith("-")) {
            nameBuilder.append("1");
            variantsHashCode = variantsHashCode.replace("-", "");
        } else {
            nameBuilder.append("0");
        }
        nameBuilder.append("0".repeat(10 - variantsHashCode.length()));
        nameBuilder.append(variantsHashCode);
        return nameBuilder.toString();
    }

    /**
     * Extracts the nucleotide sequence specified by this {@link FeatureEntry#start} and {@link FeatureEntry#end} from
     * the passed {@link FastaContainer} and adds it as this {@link FeatureEntry#nucleotideSequence}.
     *
     * @param referenceSequence {@link FastaContainer} representation of the parent/reference sequence, i.e. reference
     *                          genome or genome assembly.
     */
    public void imputeSequence(FastaContainer referenceSequence) {
        this.nucleotideSequence = referenceSequence.getSequence(this.start, this.end);
    }

    /**
     * Method to add the protein wild-type information this {@link FeatureEntry}.
     * <p>
     * If the {@link FeatureEntry#pdbFile} property is not set, this method will pass.
     * <p>
     * - The parsed protein amino-acid sequence is compared with the translated amino-acid sequence of the
     * {@link FeatureEntry#nucleotideSequence} property by pairwise sequence alignment. While the translated sequence
     * is allowed to be longer than the protein sequence, this is not allowed vice versa.
     * - The {@link FeatureEntry#structure} property will be matched in terms of residue numbers to the information
     * gained from the alignment, i.e. if the .pdb starts with residue 10,
     * but the sequences align at the beginning the first residue number in the internal .pdb is set to be 1; if the
     * .pdb starts with residue 1, but the sequence (of the protein) has a gap of length 9 at the beginning in the
     * alignment the first residue number in the internal .pdb is set to be 10.
     *
     * @throws IOException        If the .pdb file pointed to by the {@link FeatureEntry#pdbFile} can not be parsed.
     * @throws MusialBioException If either the translated {@link FeatureEntry#nucleotideSequence} yields internal
     *                            termination codons or contains any gaps when aligned to the amino-acid sequence derived from the .pdb fiel.
     */
    public void imputeProtein() throws IOException, MusialBioException {
        if (this.pdbFile == null) {
            return;
        }
        this.isCodingSequence = true;
        Structure pdbStructure = IO.readStructure(this.pdbFile);
        HashMap<String, String> proteinSequences = IO.getSequencesFromPdbStructure(pdbStructure);
        // TODO: Check if proteinSequences are identical, if more than one is contained in the .pdb model.
        String chainId;
        String chainSeq;
        char[] alignedChainSequence;
        this.translatedNucleotideSequence = Bio.translateNucSequence(this.nucleotideSequence, true, true,
                this.isSense);
        if (this.translatedNucleotideSequence.endsWith(String.valueOf(Bio.TERMINATION_AA1))) {
            if (this.translatedNucleotideSequence.substring(0, this.translatedNucleotideSequence.length() - 1).contains(String.valueOf(Bio.TERMINATION_AA1))) {
                Logging.logWarning("Feature " + this.name + " contains internal terminations in translated feature nucleotide sequence.");
            }
        } else {
            Logging.logWarning("Feature " + this.name + " does not end with a translated termination and may be inappropriate.");
        }
        char[] alignedTranslatedNucleotideSequence;
        StringBuilder paddedProteinSequenceBuilder = new StringBuilder();
        List<String> divergentSegments;
        for (Map.Entry<String, String> proteinSequenceEntry : proteinSequences.entrySet()) {
            paddedProteinSequenceBuilder.setLength(0);
            chainId = proteinSequenceEntry.getKey();
            chainSeq = proteinSequenceEntry.getValue();
            // Align protein and transl. reference sequence to infer correct starting position and truncated prefix peptide.
            Triplet<Integer, String, String> alignedSequences =
                    Bio.globalAminoAcidSequenceAlignment(
                            chainSeq,
                            this.translatedNucleotideSequence,
                            5,
                            4,
                            Bio.GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES.FREE,
                            Bio.GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES.FREE
                    );
            alignedChainSequence = alignedSequences.getValue1().toCharArray();
            alignedTranslatedNucleotideSequence = alignedSequences.getValue2().toCharArray();
            for (int pos = 0; pos < alignedChainSequence.length; pos++) {
                if (alignedChainSequence[pos] != alignedTranslatedNucleotideSequence[pos]) {
                    if (alignedChainSequence[pos] == Bio.GAP) {
                        paddedProteinSequenceBuilder.append(Character.toLowerCase(alignedTranslatedNucleotideSequence[pos]));
                    } else if (alignedTranslatedNucleotideSequence[pos] == Bio.GAP) {
                        throw new MusialBioException("Failed to allocate protein " + this.pdbFile.getName() + " to " +
                                "feature " + this.name + " due to gaps in the aligned translated feature nucleotide sequence.");
                    }
                } else {
                    paddedProteinSequenceBuilder.append(alignedChainSequence[pos]);
                }
            }
            // Construct Iterator of current chain groups sequence numbers.
            Chain pdbChain = pdbStructure.getChain(chainId);
            List<Group> pdbChainAtomGroups = pdbChain.getAtomGroups();
            Iterator<Group> pdbChainAtomGroupIterator = pdbChainAtomGroups.iterator();
            List<Group> pdbChainAtomGroupsFixed = new ArrayList<>();
            char[] paddedChainSeqChars = paddedProteinSequenceBuilder.toString().toCharArray();
            for (int chainPosition = 1; chainPosition < paddedChainSeqChars.length + 1; chainPosition++) {
                if (Character.isLowerCase(paddedChainSeqChars[chainPosition - 1])) {
                    continue;
                }
                if (pdbChainAtomGroupIterator.hasNext()) {
                    Group nextGroup = pdbChainAtomGroupIterator.next();
                    nextGroup.getResidueNumber().setSeqNum(chainPosition);
                    pdbChainAtomGroupsFixed.add(nextGroup);
                } else {
                    break;
                }
            }
            pdbChain.setAtomGroups(pdbChainAtomGroupsFixed);
            divergentSegments = Arrays.stream(
                    paddedProteinSequenceBuilder.toString().split("(?=\\p{Upper})")
            ).filter(s -> s.length() > 1).collect(Collectors.toList());
            if (divergentSegments.size() > 2) {
                Logging.logWarning("Feature " + this.name + " disaccords in " + divergentSegments.size() + " segments " +
                        "with allocated protein " + this.pdbFile.getName() + " chain " + chainId + ": The structure may be inappropriate.");
            }
            this.proteinSequences.put(chainId, paddedProteinSequenceBuilder.toString());
        }
        this.structure = String.join(IO.LINE_SEPARATOR, pdbStructure.toPDB());
    }

    /**
     * Adds a new {@link ProteoformEntry} to {@link FeatureEntry#proteoforms}.
     *
     * @param sampleId The {@link String} sample identifier of any {@link SampleEntry} from which the proteoform was derived.
     * @param variants {@link ConcurrentSkipListMap} of {@link String}s that maps positions wrt. the reference wild-type
     *                 proteoform to alternate amino-acid contents.
     */
    public void addProteoform(String sampleId, ConcurrentSkipListMap<String, String> variants) {
        String concatVariants = variants.entrySet().stream().map(e -> e.getValue() + "#" + e.getKey()).collect(Collectors.joining("\\."));
        float referenceProteinLength = (float) (this.translatedNucleotideSequence.length());
        DecimalFormat decimalFormat = new DecimalFormat("#.#");
        String proteoformName = ProteoformEntry.generateProteoformName(concatVariants);
        if (this.proteoforms.containsKey(proteoformName)) {
            this.proteoforms.get(proteoformName).samples.add(sampleId);
        } else {
            this.proteoforms.put(proteoformName, new ProteoformEntry(proteoformName, sampleId, concatVariants));
            for (String variantPosition : variants.keySet()) {
                if (!this.aminoacidVariants.containsKey(variantPosition)) {
                    this.aminoacidVariants.put(variantPosition, new ConcurrentSkipListMap<>());
                }
                String variantContent = variants.get(variantPosition);
                if (variantContent.equals(String.valueOf(Bio.TERMINATION_AA1))) {
                    this.proteoforms.get(proteoformName).annotations.put(ProteoformEntry.PROPERTY_NAME_DIVERGING_TERMINATION_POSITION, variantPosition);
                    this.proteoforms.get(proteoformName).annotations.put(ProteoformEntry.PROPERTY_NAME_DIVERGING_TERMINATION_TRUNCATED_PERCENTAGE, decimalFormat.format(100 * (1 - (Float.parseFloat(variantPosition) / referenceProteinLength))).replace(",", "."));
                }
                if (!this.aminoacidVariants.get(variantPosition).containsKey(variantContent)) {
                    AminoacidVariantEntry aminoacidVariantAnnotationEntry = new AminoacidVariantEntry();
                    // TODO: Infer causative nucleotide variants.
                    this.aminoacidVariants.get(variantPosition).put(variantContent, aminoacidVariantAnnotationEntry);
                }
                this.aminoacidVariants.get(variantPosition).get(variantContent).occurrence.add(proteoformName);
            }
            if (!this.proteoforms.get(proteoformName).annotations.containsKey(ProteoformEntry.PROPERTY_NAME_DIVERGING_TERMINATION_POSITION)) {
                this.proteoforms.get(proteoformName).annotations.put(ProteoformEntry.PROPERTY_NAME_DIVERGING_TERMINATION_POSITION, "-1");
                this.proteoforms.get(proteoformName).annotations.put(ProteoformEntry.PROPERTY_NAME_DIVERGING_TERMINATION_TRUNCATED_PERCENTAGE, "0.0");
            }
            // Compute statistics regarding the variability of the proteoform; Only positions before the first termination are considered.
            ArrayList<String> variantPositions = new ArrayList<>(variants.keySet());
            int referenceSegmentLength;
            if (!Objects.equals(this.proteoforms.get(proteoformName).annotations.get(ProteoformEntry.PROPERTY_NAME_DIVERGING_TERMINATION_POSITION), "-1")) {
                int firstTerminationPosition = Integer.parseInt(this.proteoforms.get(proteoformName).annotations.get(ProteoformEntry.PROPERTY_NAME_DIVERGING_TERMINATION_POSITION));
                variantPositions = (ArrayList<String>) variantPositions.stream().filter(p -> Integer.parseInt(p) <= firstTerminationPosition).collect(Collectors.toList());
                referenceSegmentLength = firstTerminationPosition;
            } else {
                referenceSegmentLength = (int) referenceProteinLength;
            }
            // (1) Percentage of variable positions wrt. reference protein length.
            this.proteoforms.get(proteoformName).annotations.put(ProteoformEntry.PROPERTY_NAME_VARIABLE_POSITIONS_TOTAL, decimalFormat.format(100L * (variantPositions.size() / referenceSegmentLength)).replace(",", "."));
            // (2) Conglomeration index; i.e., the p-value of a KS-Test of the observed variant positions compared to a uniform distribution of variant positions.
            double[] dArrVariantPositions = variantPositions.stream().mapToDouble(Double::valueOf).toArray();
            this.proteoforms.get(proteoformName).annotations.put(ProteoformEntry.PROPERTY_NAME_CONGLOMERATION_INDEX, String.valueOf(TestUtils.exactP(new KolmogorovSmirnovTest().kolmogorovSmirnovTest(new UniformRealDistribution(1.0, 10.0), dArrVariantPositions, true), dArrVariantPositions.length, dArrVariantPositions.length, false)));
        }
    }

    /**
     * Adds a new {@link ProteoformEntry} to {@link FeatureEntry#proteoforms}.
     *
     * @param sampleId The {@link String} sample identifier of any {@link SampleEntry} from which the proteoform was derived.
     * @param variants {@link ConcurrentSkipListMap} of {@link String}s that maps positions wrt. the reference wild-type
     *                 proteoform to alternate amino-acid contents.
     */
    public void addGenotype(String sampleId, ConcurrentSkipListMap<Integer, String> variants) {
        String concatVariants = variants.entrySet().stream().map(e -> e.getValue() + "#" + e.getKey()).collect(Collectors.joining("\\."));
        float referenceGeneLength = (float) (this.nucleotideSequence.length());
        DecimalFormat decimalFormat = new DecimalFormat("#.#");
        String genotypeName = GenotypeEntry.generateGenotypeName(concatVariants);
        if (this.genotypes.containsKey(genotypeName)) {
            this.genotypes.get(genotypeName).samples.add(sampleId);
        } else {
            this.genotypes.put(genotypeName, new GenotypeEntry(genotypeName, sampleId, concatVariants));
            for (int variantPosition : variants.keySet()) {
                if (!this.nucleotideVariants.containsKey(variantPosition)) {
                    this.nucleotideVariants.put(variantPosition, new ConcurrentSkipListMap<>());
                }
                String variantContent = variants.get(variantPosition);
                if (!this.nucleotideVariants.get(variantPosition).containsKey(variantContent)) {
                    NucleotideVariantEntry nucleotideVariantEntry = new NucleotideVariantEntry();
                    this.nucleotideVariants.get(variantPosition).put(variantContent, nucleotideVariantEntry);
                }
                this.nucleotideVariants.get(variantPosition).get(variantContent).occurrence.add(genotypeName);
            }
            // Compute statistics regarding the variability of the proteoform; Only positions before the first termination are considered.
            // ...
        }
    }

}