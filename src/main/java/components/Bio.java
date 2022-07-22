package components;

import com.google.common.base.Splitter;
import datastructure.FeatureEntry;
import datastructure.VariantsDictionary;
import exceptions.MusialBioException;
import org.javatuples.Triplet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;

/**
 * Comprises static methods used to solve specifically bioinformatics problems.
 *
 * @author Simon Hackl
 * @version 2.1
 * @since 2.0
 */
public final class Bio {

    /**
     * Enum to store different modes to handle prefix gaps for global sequence alignment.
     */
    public enum GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES {
        /**
         * Gaps at the end are not penalized.
         */
        FREE,
        /**
         * Gaps at the end are penalized normally.
         */
        PENALIZE,
        /**
         * Gaps at the end are not allowed.
         */
        FORBID
    }

    /**
     * One-letter code character used to indicate a translated stop codon.
     */
    public static final char TERMINATION_AA1 = '*';
    /**
     * Three-letter code String used to indicate a translated stop codon.
     */
    public static final String TERMINATION_AA3 = "TER";
    /**
     * One-letter code character used to indicate the translation of an incomplete codon.
     */
    public static final char ANY_AA1 = 'X';
    /**
     * Three-letter code string used to indicate the translation of an incomplete codon.
     */
    public static final String ANY_AA3 = "ANY";
    /**
     * One-letter code character used to indicate the deletion of an amino-acid.
     */
    public static final char DELETION_AA1 = '-';
    /**
     * Three-letter code string used to indicate the deletion of an amino-acid.
     */
    @SuppressWarnings("unused")
    public static final String DELETION_AA3 = "DEL";
    /**
     * One-letter character used to indicate an alignment gap.
     */
    public static final char GAP = '-';
    /**
     * Placeholder string to indicate no content.
     */
    @SuppressWarnings("unused")
    public static final String NONE = "None";
    /**
     * Hash map mapping nucleotide codons to single-letter amino acids. Stop codons map to no character.
     */
    public static final HashMap<String, String> CODON_MAP = new HashMap<>() {{
        put("TTT", "F");
        put("TTC", "F");
        put("TTA", "L");
        put("TTG", "L");
        put("CTT", "L");
        put("CTC", "L");
        put("CTA", "L");
        put("CTG", "L");
        put("ATT", "I");
        put("ATC", "I");
        put("ATA", "I");
        put("ATG", "M");
        put("GTT", "V");
        put("GTC", "V");
        put("GTA", "V");
        put("GTG", "V");
        put("TCT", "S");
        put("TCC", "S");
        put("TCA", "S");
        put("TCG", "S");
        put("CCT", "P");
        put("CCC", "P");
        put("CCA", "P");
        put("CCG", "P");
        put("ACT", "T");
        put("ACC", "T");
        put("ACA", "T");
        put("ACG", "T");
        put("GCT", "A");
        put("GCC", "A");
        put("GCA", "A");
        put("GCG", "A");
        put("TAT", "Y");
        put("TAC", "Y");
        put("TAA", "");
        put("TAG", "");
        put("CAT", "H");
        put("CAC", "H");
        put("CAA", "Q");
        put("CAG", "Q");
        put("AAT", "N");
        put("AAC", "N");
        put("AAA", "K");
        put("AAG", "K");
        put("GAT", "D");
        put("GAC", "D");
        put("GAA", "E");
        put("GAG", "E");
        put("TGT", "C");
        put("TGC", "C");
        put("TGA", "");
        put("TGG", "W");
        put("CGT", "R");
        put("CGC", "R");
        put("CGA", "R");
        put("CGG", "R");
        put("AGT", "S");
        put("AGC", "S");
        put("AGA", "R");
        put("AGG", "R");
        put("GGT", "G");
        put("GGC", "G");
        put("GGA", "G");
        put("GGG", "G");
    }};
    /**
     * Hash map mapping amino-acid one to three-letter codes.
     */
    public static final HashMap<String, String> AA1TO3 = new HashMap<>() {{
        put("A", "ALA");
        put("R", "ARG");
        put("N", "ASN");
        put("D", "ASP");
        put("C", "CYS");
        put("E", "GLU");
        put("Q", "GLN");
        put("G", "GLY");
        put("H", "HIS");
        put("I", "ILE");
        put("L", "LEU");
        put("K", "LYS");
        put("M", "MET");
        put("F", "PHE");
        put("P", "PRO");
        put("S", "SER");
        put("T", "THR");
        put("W", "TRP");
        put("Y", "TYR");
        put("V", "VAL");
        put(String.valueOf(ANY_AA1), ANY_AA3);
        put(String.valueOf(TERMINATION_AA1), TERMINATION_AA3);
    }};
    /**
     * Hash map mapping amino-acid one letter symbols to indices used to access substitution matrix values.
     */
    public static final HashMap<Character, Integer> AA1_PAM120_INDEX = new HashMap<>() {{
        put('A', 0);
        put('R', 1);
        put('N', 2);
        put('D', 3);
        put('C', 4);
        put('Q', 5);
        put('E', 6);
        put('G', 7);
        put('H', 8);
        put('I', 9);
        put('L', 10);
        put('K', 11);
        put('M', 12);
        put('F', 13);
        put('P', 14);
        put('S', 15);
        put('T', 16);
        put('W', 17);
        put('Y', 18);
        put('V', 19);
        put(ANY_AA1, 20);
        put(TERMINATION_AA1, 21);
    }};
    /**
     * The PAM120 matrix used for alignment computation.
     * <p>
     * - Computed with <a href="https://bioinformaticshome.com/online_software/make_pam/makePAMmatrix.html">makePAMmatrix.html</a>
     * and a scaling factor of 1.
     * - Includes values for 'X'/any amino-acid (match/mismatch: -1) and '*'/termination (match/mismatch: -4/-1).
     */
    private static final int[][] PAM120 = {
            {1, -1, 0, 0, -1, 0, 0, 0, -1, 0, -1, -1, -1, -1, 0, 0, 0, -2, -1, 0, -1, -4},
            {-1, 2, 0, -1, -1, 0, -1, -1, 0, -1, -1, 1, 0, -2, 0, 0, -1, 0, -2, -1, -1, -4},
            {0, 0, 1, 1, -2, 0, 0, 0, 1, -1, -1, 0, -1, -1, -1, 0, 0, -2, -1, -1, -1, -4},
            {0, -1, 1, 2, -2, 0, 1, 0, 0, -1, -2, 0, -1, -2, -1, 0, 0, -3, -2, -1, -1, -4},
            {-1, -1, -2, -2, 3, -2, -2, -2, -1, -1, -3, -2, -2, -2, -1, 0, -1, -3, 0, -1, -1, -4},
            {0, 0, 0, 0, -2, 2, 1, -1, 1, -1, -1, 0, 0, -2, 0, -1, -1, -2, -2, -1, -1, -4},
            {0, -1, 0, 1, -2, 1, 2, 0, 0, -1, -2, 0, -1, -2, -1, 0, -1, -3, -2, -1, -1, -4},
            {0, -1, 0, 0, -2, -1, 0, 2, -1, -1, -2, -1, -1, -2, -1, 0, 0, -3, -2, -1, -1, -4},
            {-1, 0, 1, 0, -1, 1, 0, -1, 2, -1, -1, -1, -1, -1, 0, -1, -1, -1, 0, -1, -1, -4},
            {0, -1, -1, -1, -1, -1, -1, -1, -1, 2, 0, -1, 1, 0, -1, -1, 0, -2, -1, 1, -1, -4},
            {-1, -1, -1, -2, -3, -1, -2, -2, -1, 0, 2, -1, 1, 0, -1, -1, -1, -1, -1, 0, -1, -4},
            {-1, 1, 0, 0, -2, 0, 0, -1, -1, -1, -1, 2, 0, -2, -1, 0, 0, -2, -2, -1, -1, -4},
            {-1, 0, -1, -1, -2, 0, -1, -1, -1, 1, 1, 0, 3, 0, -1, -1, 0, -2, -1, 0, -1, -4},
            {-1, -2, -1, -2, -2, -2, -2, -2, -1, 0, 0, -2, 0, 3, -2, -1, -1, 0, 2, -1, -1, -4},
            {0, 0, -1, -1, -1, 0, -1, -1, 0, -1, -1, -1, -1, -2, 2, 0, 0, -2, -2, -1, -1, -4},
            {0, 0, 0, 0, 0, -1, 0, 0, -1, -1, -1, 0, -1, -1, 0, 1, 1, -1, -1, -1, -1, -4},
            {0, -1, 0, 0, -1, -1, -1, 0, -1, 0, -1, 0, 0, -1, 0, 1, 1, -2, -1, 0, -1, -4},
            {-2, 0, -2, -3, -3, -2, -3, -3, -1, -2, -1, -2, -2, 0, -2, -1, -2, 4, -1, -3, -1, -4},
            {-1, -2, -1, -2, 0, -2, -2, -2, 0, -1, -1, -2, -1, 2, -2, -1, -1, -1, 3, -1, -1, -4},
            {0, -1, -1, -1, -1, -1, -1, -1, -1, 1, 0, -1, 0, -1, -1, -1, 0, -3, -1, 2, -1, -4},
            {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, -4},
            {-4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, 0}
    };

    /**
     * Returns an amino-acid representation of the passed `codon`.
     *
     * @param codon              {@link String} representing the nucleotide codon to translate.
     * @param asAA3              {@link Boolean} whether the codon should be translated to the amino-acid three-letter code or not.
     * @param includeTermination {@link Boolean} whether a representation for translation termination (in the case of
     *                           three-letter code 'TER' and in the case of one-letter code 'Z') shall
     *                           be returned or not, i.e. an empty String is returned instead.
     * @param includeIncomplete  {@link Boolean} whether incomplete codons, i.e. with a length other than 3, should be
     *                           translated as incomplete amino-acids.
     * @return {@link String} representing a translated amino-acid.
     * @throws MusialBioException If the passed codon has a length other than three and `includeIncomplete` is false.
     */
    public static String translateCodon(String codon, boolean asAA3, boolean includeTermination,
                                        boolean includeIncomplete)
            throws MusialBioException {
        if (codon.length() != 3) {
            if (!includeIncomplete) {
                throw new MusialBioException("Unable to translate codon " + codon + " with length different from three.");
            } else {
                if (asAA3) {
                    return ANY_AA3;
                } else {
                    return String.valueOf(ANY_AA1);
                }
            }
        } else if (codon.contains("N")) {
            if (asAA3) {
                return ANY_AA3;
            } else {
                return String.valueOf(ANY_AA1);
            }
        } else if (CODON_MAP.get(codon).equals("") && includeTermination) {
            if (asAA3) {
                return TERMINATION_AA3;
            } else {
                return String.valueOf(TERMINATION_AA1);
            }
        } else {
            if (CODON_MAP.get(codon).equals("")) {
                return "";
            } else {
                if (asAA3) {
                    return AA1TO3.get(CODON_MAP.get(codon));
                } else {
                    return CODON_MAP.get(codon);
                }
            }
        }
    }

    /**
     * Translates a nucleotide sequence, split into codons of length three, into a single-letter amino acid sequence.
     *
     * @param splitNucSequence   {@link ArrayList<String>} representing a nucleotide sequence that was split into codons
     *                           of length 3.
     * @param includeTermination {@link Boolean} whether a representation for translation termination (in the case of
     *                           three-letter code 'TER' and in the case of one-letter code 'Z') shall
     *                           be returned or not, i.e. an empty String is returned instead.
     * @param includeIncomplete  {@link Boolean} whether an incomplete amino-acid should be added to the end if the
     *                           sequence contains an incomplete codon at the end.
     * @return {@link String} representing the translated nucleotide sequence.
     * @throws MusialBioException If any codon with a length different than three is detected.
     */
    public static String translateNucSequence(Iterable<String> splitNucSequence, boolean includeTermination,
                                              boolean includeIncomplete) throws MusialBioException {
        StringBuilder translatedNucSequenceBuilder = new StringBuilder();
        for (String s : splitNucSequence) {
            if (s.length() != 3) {
                if (!includeIncomplete) {
                    throw new MusialBioException("Failed to translate nucleotide sequence containing codon of length unequal 3.");
                }
            } else {
                translatedNucSequenceBuilder.append(translateCodon(s, false, includeTermination, includeIncomplete));
            }
        }
        return translatedNucSequenceBuilder.toString();
    }

    /**
     * Translates a nucleotide sequence into a single-letter amino acid sequence.
     *
     * @param nucSequence        {@link String} representing a nucleotide sequence.
     * @param includeTermination {@link Boolean} whether a representation for translation termination (in the case of
     *                           three-letter code 'TER' and in the case of one-letter code 'Z') shall
     *                           be returned or not, i.e. an empty String is returned instead.
     * @param includeIncomplete  {@link Boolean} whether an incomplete amino-acid should be added to the end if the
     *                           sequence contains an incomplete codon at the end.
     * @param asSense            {@link Boolean} whether the sequence shall be translated as sense or anti-sense.
     * @return {@link String} representing the translated nucleotide sequence.
     * @throws MusialBioException If any codon with a length different than three is detected.
     */
    public static String translateNucSequence(String nucSequence, boolean includeTermination,
                                              boolean includeIncomplete, boolean asSense) throws MusialBioException {
        if (!asSense) {
            nucSequence = reverseComplement(nucSequence);
        }
        Iterable<String> splitNucSequence = Splitter.fixedLength(3).split(nucSequence);
        return translateNucSequence(splitNucSequence, includeTermination, includeIncomplete);
    }

    /**
     * Returns the reverse complement of the passed nucleotide sequence.
     *
     * @param sequence {@link String} representing a nucleotide sequence.
     * @return {@link String} representing the reverse complement of the passed sequence.
     */
    public static String reverseComplement(String sequence) {
        StringBuilder reverseComplementBuilder = new StringBuilder();
        char[] sequenceCharArray = sequence.toCharArray();
        for (int i = sequenceCharArray.length - 1; i >= 0; i--) {
            char sequenceAtI = sequenceCharArray[i];
            reverseComplementBuilder.append(invertBase(sequenceAtI));
        }
        return reverseComplementBuilder.toString();
    }

    /**
     * Returns the complement of a nucleotide base, i.e. switches A to T, C to G and vice versa. For non nucleotide
     * symbols the identity is returned.
     *
     * @param base {@link Character} base to invert.
     * @return {@link Character} the inverted base.
     */
    public static Character invertBase(char base) {
        return switch (base) {
            case 'A' -> 'T';
            case 'C' -> 'G';
            case 'G' -> 'C';
            case 'T' -> 'A';
            default -> base;
        };
    }

    /**
     * Invokes the {@link Bio#globalSequenceAlignment} method with pre-specified parameters for nucleotide sequence
     * alignment.
     * <p>
     * - Uses a simple substitution matrix that scores matches with 1 and mismatches with -1.
     * - Uses a gap open and extension penalty of -2 and -1, respectively.
     *
     * @param nucSeq1    {@link String} representation of the first nucleotide sequence for alignment.
     * @param nucSeq2    {@link String} representation of the second nucleotide sequence for alignment.
     * @param left_mode  {@link GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES} value to indicate how to handle left-marginal gaps.
     * @param right_mode {@link GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES} value to indicate how to handle right-marginal gaps.
     * @return {@link Triplet} storing the alignment score, the aligned first sequence and the aligned second sequence.
     */
    public static Triplet<Integer, String, String> globalNucleotideSequenceAlignment(String nucSeq1, String nucSeq2,
                                                                                     GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES left_mode,
                                                                                     GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES right_mode) {
        HashMap<Character, Integer> simpleNucleotideScoringMatrixIndexMap = new HashMap<>() {{
            put('A', 0);
            put('C', 1);
            put('G', 2);
            put('T', 3);
            put('N', 4);
        }};
        int[][] simpleNucleotideScoringMatrix = {
                {1, -1, -1, -1, -1},
                {-1, 1, -1, -1, -1},
                {-1, -1, 1, -1, -1},
                {-1, -1, -1, 1, -1},
                {-1, -1, -1, -1, 1},
        };
        return globalSequenceAlignment(nucSeq1, nucSeq2, simpleNucleotideScoringMatrixIndexMap,
                simpleNucleotideScoringMatrix,
                2, 1, left_mode, right_mode);
    }

    /**
     * Invokes the {@link Bio#globalSequenceAlignment} method with pre-specified parameters for amino-acid sequence
     * alignment.
     * <p>
     * - Uses the PAM120 substitution matrix.
     *
     * @param aaSeq1           {@link String} representation of the first amino-acid sequence for alignment.
     * @param aaSeq2           {@link String} representation of the second amino-acid sequence for alignment.
     * @param gapOpenPenalty   {@link Integer} (positive!) to use as gap open penalty.
     * @param gapExtendPenalty {@link Integer} (positive!) to use as gap extension penalty.
     * @param left_mode        {@link GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES} value to indicate how to handle left-marginal gaps.
     * @param right_mode       {@link GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES} value to indicate how to handle right-marginal gaps.
     * @return {@link Triplet} storing the alignment score, the aligned first sequence and the aligned second sequence.
     */
    public static Triplet<Integer, String, String> globalAminoAcidSequenceAlignment(String aaSeq1, String aaSeq2,
                                                                                    int gapOpenPenalty,
                                                                                    int gapExtendPenalty,
                                                                                    GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES left_mode,
                                                                                    GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES right_mode) {
        return globalSequenceAlignment(aaSeq1, aaSeq2, AA1_PAM120_INDEX, PAM120, gapOpenPenalty, gapExtendPenalty,
                left_mode, right_mode);
    }

    /**
     * Computes a global sequence alignment using the gap-affine Needleman-Wunsch algorithm.
     *
     * @param seq1                  {@link String} representation of the first sequence for alignment.
     * @param seq2                  {@link String} representation of the second sequence for alignment.
     * @param scoringMatrixIndexMap {@link HashMap} mapping characters that may occur in the aligned sequences to integer indices of the scoring matrix.
     * @param scoringMatrix         {@link Integer[][]} containing the scoring values for each pair of characters that may occur in the aligned sequences.
     * @param gapOpenPenalty        {@link Integer} value used to penalize the opening of a gap.
     * @param gapExtendPenalty      {@link Integer} value used to penalize the extension of a gap.
     * @param left_mode             {@link GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES} value to indicate how to handle left-marginal gaps.
     * @param right_mode            {@link GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES} value to indicate how to handle right-marginal gaps.
     * @return {@link Triplet} storing the alignment score, the aligned first sequence and the aligned second sequence.
     */
    public static Triplet<Integer, String, String> globalSequenceAlignment(String seq1, String seq2,
                                                                           HashMap<Character, Integer> scoringMatrixIndexMap,
                                                                           int[][] scoringMatrix, int gapOpenPenalty,
                                                                           int gapExtendPenalty,
                                                                           GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES left_mode,
                                                                           GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES right_mode) {
    /*
    Notes on the alignment matrices:
    - The y-axis will yield seq1.
    - The x-axis will yield seq2
    - Indels are wrt. seq1; thus insertions are traced back by walking vertically and deletions are traced back by
    walking horizontally in the matrix.
     */
        int[][] alignmentMatrix = new int[seq1.length() + 1][seq2.length() + 1];
        int[][] matchScoreMatrix = new int[seq1.length() + 1][seq2.length() + 1];
        int[][] insertionScoreMatrix = new int[seq1.length() + 1][seq2.length() + 1];
        int[][] deletionScoreMatrix = new int[seq1.length() + 1][seq2.length() + 1];
        char[][] tracebackMatrix = new char[seq1.length() + 1][seq2.length() + 1];
        char[] seq1Array = seq1.toCharArray();
        char[] seq2Array = seq2.toCharArray();
        int alignmentScore;
        StringBuilder seq1Builder = new StringBuilder();
        StringBuilder seq2Builder = new StringBuilder();
    /*
    (1) Compute global sequence alignment.
     */
        alignmentMatrix[0][0] = 0;
        matchScoreMatrix[0][0] = 0;
        insertionScoreMatrix[0][0] = 0;
        deletionScoreMatrix[0][0] = 0;
        int gapCost;
        // i -> PREFIX
        for (int i = 1; i < seq1.length() + 1; i++) {
            gapCost = switch (left_mode) {
                case FREE -> 0;
                case PENALIZE -> -gapOpenPenalty - (i - 1) * gapExtendPenalty;
                case FORBID -> -gapOpenPenalty * seq1.length();
            };
            alignmentMatrix[i][0] = gapCost;
            matchScoreMatrix[i][0] = gapCost;
            insertionScoreMatrix[i][0] = gapCost;
            deletionScoreMatrix[i][0] = gapCost;
            tracebackMatrix[i][0] = 'I';
        }
        // j -> SUFFIX
        for (int j = 1; j < seq2.length() + 1; j++) {
            gapCost = switch (right_mode) {
                case FREE -> 0;
                case PENALIZE -> -gapOpenPenalty - (j - 1) * gapExtendPenalty;
                case FORBID -> -gapOpenPenalty * seq2.length();
            };
            alignmentMatrix[0][j] = gapCost;
            matchScoreMatrix[0][j] = gapCost;
            insertionScoreMatrix[0][j] = gapCost;
            deletionScoreMatrix[0][j] = gapCost;
            tracebackMatrix[0][j] = 'D';
        }
        double max;
        for (int i = 1; i < seq1.length() + 1; i++) {
            for (int j = 1; j < seq2.length() + 1; j++) {
                matchScoreMatrix[i][j] =
                        alignmentMatrix[i - 1][j - 1]
                                + scoringMatrix[scoringMatrixIndexMap.get(seq1Array[i - 1])][scoringMatrixIndexMap
                                .get(seq2Array[j - 1])];
                insertionScoreMatrix[i][j] =
                        Math.max(
                                alignmentMatrix[i - 1][j] - gapOpenPenalty,
                                insertionScoreMatrix[i - 1][j] - gapExtendPenalty
                        );
                deletionScoreMatrix[i][j] =
                        Math.max(
                                alignmentMatrix[i][j - 1] - gapOpenPenalty,
                                deletionScoreMatrix[i][j - 1] - gapExtendPenalty
                        );
                max = Integer.MIN_VALUE;
                if (insertionScoreMatrix[i][j] > max) {
                    max = insertionScoreMatrix[i][j];
                    alignmentMatrix[i][j] = (int) max;
                    tracebackMatrix[i][j] = 'I';
                }
                if (deletionScoreMatrix[i][j] > max) {
                    max = deletionScoreMatrix[i][j];
                    alignmentMatrix[i][j] = (int) max;
                    tracebackMatrix[i][j] = 'D';
                }
                if (matchScoreMatrix[i][j] > max) {
                    max = matchScoreMatrix[i][j];
                    alignmentMatrix[i][j] = (int) max;
                    tracebackMatrix[i][j] = 'M';
                }
            }
        }
        alignmentScore = alignmentMatrix[seq1.length()][seq2.length()];
    /*
    (2) Compute traceback path from global alignment.
    */
        LinkedList<Character> tracebackPath = new LinkedList<>();
        boolean constructTracebackPath = true;
        int i = seq1.length();
        int j = seq2.length();
        char tracebackDirection = tracebackMatrix[i][j];
        while (constructTracebackPath) {
            if (tracebackDirection == 'M') {
                tracebackPath.add('M');
                i = i - 1;
                j = j - 1;
            } else if (tracebackDirection == 'D') {
                tracebackPath.add('D');
                j = j - 1;
            } else if (tracebackDirection == 'I') {
                tracebackPath.add('I');
                i = i - 1;
            }
            if (i == 0 && j == 0) {
                constructTracebackPath = false;
            } else {
                tracebackDirection = tracebackMatrix[i][j];
            }
        }
        Collections.reverse(tracebackPath);
    /*
    (3) Iterate over traceback path and construct aligned nucleotide and amino-acid sequence.
    */
        int aaSeq1Index = 0;
        int aaSeq2Index = 0;
        for (Character character : tracebackPath) {
            // Start walking along the traceback path.
            tracebackDirection = character;
            if (tracebackDirection == 'M') {
                // CASE: Match or mismatch of nucleotide and amino-acid sequence.
                seq1Builder.append(seq1Array[aaSeq1Index]);
                aaSeq1Index += 1;
                seq2Builder.append(seq2Array[aaSeq2Index]);
                aaSeq2Index += 1;
            } else if (tracebackDirection == 'D') {
                // CASE: Deletion wrt. to first amino acid sequence.
                seq1Builder.append(GAP);
                seq2Builder.append(seq2Array[aaSeq2Index]);
                aaSeq2Index += 1;
            } else if (tracebackDirection == 'I') {
                // CASE: Insertion wrt. to first amino acid sequence.
                seq1Builder.append(seq1Array[aaSeq1Index]);
                aaSeq1Index += 1;
                seq2Builder.append(GAP);
            }
        }
    /*
    (6) Insert results into Triplet.
     */
        return new Triplet<>(alignmentScore, seq1Builder.toString(), seq2Builder.toString());
    }

    /**
     * Computes variants from two aligned sequences.
     * <p>
     * Variants are extracted wrt. the query versus the target sequence and formatted as p@r@a, where p is the variant
     * position, r is the reference content and a is the single alternate alleles content..
     *
     * @param targetSequence {@link String} representation of the target sequence (i.e. reference).
     * @param querySequence  {@link String} representation of the query sequence (i.e. the one with variants).
     * @return {@link ArrayList} containing derived variants, c.f. method description for format details.
     */
    public static ArrayList<String> getVariantsOfAlignedSequences(String targetSequence,
                                                                  String querySequence) {
        // FIXME: Same procedure as in inferProteoform::extractVariantsFromAlignment.
        ArrayList<String> variants = new ArrayList<>();
        StringBuilder variantBuilder = new StringBuilder();
        StringBuilder referenceBuilder = new StringBuilder();
        char[] targetSequenceArray = targetSequence.toCharArray();
        char[] querySequenceArray = querySequence.toCharArray();
        char targetContent;
        char queryContent;
        int variantStart = 1;
        int alignmentLength = targetSequence.length();
        boolean isSubstitution = false;
        boolean isInsertion = false;
        boolean isDeletion = false;
        boolean referenceHasPrefixGap = true;
        for (int i = 0; i < alignmentLength; i++) {
            targetContent = targetSequenceArray[i];
            queryContent = querySequenceArray[i];
            if (referenceHasPrefixGap && (targetContent != Bio.GAP)) {
                referenceHasPrefixGap = false;
            }
            if (targetContent == queryContent) {
                // CASE: Match.
                if (isSubstitution || isInsertion || isDeletion) {
                    variants.add(variantStart + "@" + variantBuilder + "@" + referenceBuilder);
                    variantBuilder.setLength(0);
                    referenceBuilder.setLength(0);
                }
                isSubstitution = false;
                isInsertion = false;
                isDeletion = false;
            } else if (targetContent == Bio.GAP) {
                // CASE: Insertion (in variant).
                if (isSubstitution || isDeletion) {
                    variants.add(variantStart + "@" + variantBuilder + "@" + referenceBuilder);
                    variantBuilder.setLength(0);
                    referenceBuilder.setLength(0);
                }
                if (!isInsertion) {
                    if (referenceHasPrefixGap) {
                        variantStart = 0;
                    } else {
                        variantBuilder.append(targetSequenceArray[i - 1]);
                        variantStart = i;
                        referenceBuilder.append(targetSequenceArray[i - 1]);
                    }
                }
                variantBuilder.append(queryContent);
                isSubstitution = false;
                isInsertion = true;
                isDeletion = false;
            } else if (queryContent == Bio.GAP) {
                // CASE: Deletion (in variant).
                if (isSubstitution || isInsertion) {
                    variants.add(variantStart + "@" + variantBuilder + "@" + referenceBuilder);
                    variantBuilder.setLength(0);
                    referenceBuilder.setLength(0);
                }
                if (!isDeletion) {
                    // Alignment is forbidden to start with a gap.
                    variantBuilder.append(targetSequenceArray[i - 1]);
                    variantStart = i;
                    referenceBuilder.append(targetSequenceArray[i - 1]);
                }
                referenceBuilder.append(targetContent);
                variantBuilder.append(queryContent);
                isSubstitution = false;
                isInsertion = false;
                isDeletion = true;
            } else {
                // CASE: Mismatch/Substitution.
                if (isDeletion || isInsertion) {
                    variants.add(variantStart + "@" + variantBuilder + "@" + referenceBuilder);
                    variantBuilder.setLength(0);
                    referenceBuilder.setLength(0);
                }
                if (!isSubstitution) {
                    variantStart = i + 1;
                }
                variantBuilder.append(queryContent);
                referenceBuilder.append(targetContent);
                isSubstitution = true;
                isInsertion = false;
                isDeletion = false;
            }
        }
        if (isSubstitution || isInsertion || isDeletion) {
            variants.add(variantStart + "@" + variantBuilder + "@" + referenceBuilder);
            variantBuilder.setLength(0);
            referenceBuilder.setLength(0);
        }
        return variants;
    }

    /**
     * Infers the proteoform variants of a single sample wrt. a single feature from the information of the passed
     * variantsDictionary.
     * <p>
     * - The computed variants maps keys are formatted as `x+y`, where x is the 1-based indexed position in the
     * reference sequence at which the variant occurs and y is the 1-based indexed number of inserted positions after
     * this position.
     * - The computed variants maps values are the respective single letter code amino-acid contents of the variants
     * including the content at the variant position.
     *
     * @param variantsDictionary {@link VariantsDictionary} instance which content is used to infer proteoform information.
     * @param fId                {@link String} specifying the {@link FeatureEntry#name} of the feature to which respect
     *                           the sample proteoform should be inferred.
     * @param sId                {@link String} specifying the {@link datastructure.SampleEntry#name} of the sample of
     *                           which the proteoform should be inferred.
     * @return {@link ConcurrentSkipListMap} mapping positions to variant contents.
     * @throws MusialBioException If any translation procedure of nucleotide sequences fails.
     */
    public static ConcurrentSkipListMap<String, String> inferProteoform(
            VariantsDictionary variantsDictionary, String fId, String sId)
            throws MusialBioException {
        String sampleNucleotideSequence = variantsDictionary.getNucleotideSequence(fId, sId);
        Function<Triplet<Integer, String, String>, ConcurrentSkipListMap<String, String>> extractVariantsFromAlignment = (sa) -> {
            ConcurrentSkipListMap<String, String> variants = new ConcurrentSkipListMap<>((s1, s2) -> {
                int p1 = Integer.parseInt(s1.split("\\+")[0]);
                int p2 = Integer.parseInt(s2.split("\\+")[0]);
                if (p1 != p2) {
                    return Integer.compare(p1, p2);
                } else {
                    String c1 = s1.split("\\+")[1];
                    String c2 = s2.split("\\+")[1];
                    return c1.compareTo(c2);
                }
            });
            char[] alignedReferenceCharacters;
            char alignedReferenceCharacter;
            char[] alignedSampleCharacters;
            char alignedSampleCharacter;
            alignedReferenceCharacters = sa.getValue1().toCharArray();
            alignedSampleCharacters = sa.getValue2().toCharArray();
            int consecutiveInsertionCount = 0;
            int totalInsertionCount = 0;
            for (int i = 0; i < alignedSampleCharacters.length; i++) {
                alignedReferenceCharacter = alignedReferenceCharacters[i];
                alignedSampleCharacter = alignedSampleCharacters[i];
                if (alignedReferenceCharacter != alignedSampleCharacter) {
                    if (alignedSampleCharacter == Bio.DELETION_AA1) {
                        // CASE: Deletion in sample sequence.
                        consecutiveInsertionCount = 0;
                    } else if (alignedReferenceCharacter == Bio.DELETION_AA1) {
                        // CASE: Insertion in sample.
                        consecutiveInsertionCount++;
                        totalInsertionCount++;
                    } else {
                        // CASE: Substitution in sample.
                        consecutiveInsertionCount = 0;
                    }
                    variants.put(i - totalInsertionCount + 1 + "+" + consecutiveInsertionCount, String.valueOf(alignedSampleCharacter));
                } else {
                    consecutiveInsertionCount = 0;
                }
            }
            return variants;
        };
        if (variantsDictionary.features.get(fId).allocatedProtein != null
                && !sId.equals(VariantsDictionary.WILD_TYPE_SAMPLE_ID)
                && sampleNucleotideSequence != null) {
            sampleNucleotideSequence = sampleNucleotideSequence.replace("-", "");
            FeatureEntry featureEntry = variantsDictionary.features.get(fId);
            String referenceProteinSequence = Bio.translateNucSequence(featureEntry.nucleotideSequence, true, true, featureEntry.isSense);
            String sampleProteinSequence = Bio.translateNucSequence(sampleNucleotideSequence, true, true, featureEntry.isSense);
            Triplet<Integer, String, String> sa = Bio.globalAminoAcidSequenceAlignment(
                    referenceProteinSequence,
                    sampleProteinSequence,
                    4,
                    3,
                    GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES.FORBID,
                    GLOBAL_SEQUENCE_ALIGNMENT_MARGIN_GAP_MODES.PENALIZE
            );
            return extractVariantsFromAlignment.apply(sa);
        } else {
            return new ConcurrentSkipListMap<>();
        }
    }
}