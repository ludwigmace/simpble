package secretshare;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the output of the combineParanoid() operation.
 * "Paranoid" is the term used when:
 *    "given more shares than needed, check (all) combinations of shares,
 *     make sure that _each_ combination of shares returns the same secret"
 *
 */
public class ParanoidOutput {
	
    public Integer maximumCombinationsAllowedToTest; // null means "all"
    
    public BigInteger totalNumberOfCombinations;
    
    private final List<String> combinations = new ArrayList<String>();
    
    public BigInteger agreedAnswerEveryTime;
    
    public ParanoidOutput() {
    	
    }
    
    
    public String getParanoidCompleteOutput()
    {
        String ret = getParanoidHeaderOutput();
        ret += getParanoidCombinationOutput();
        return ret;
    }

    public String getParanoidHeaderOutput()
    {
        String ret = "SecretShare.paranoid(max=" +
                    ((maximumCombinationsAllowedToTest != null) ? maximumCombinationsAllowedToTest : "all") +
                    " combo.total=" +
                    totalNumberOfCombinations +
                    ")";
        ret += "\n";
        return ret;
    }

    public String getParanoidCombinationOutput()
    {
        String ret = "";
        for (String s : combinations)
        {
            ret += s;
            ret += "\n";
        }

        return ret;
    }

    public void recordCombination(BigInteger currentCombinationNumber,
                                  String indexesAsString,
                                  String dumpshares)
    {
        String s = "Combination: " +
                    currentCombinationNumber +
                    " of " +
                    totalNumberOfCombinations +
                    indexesAsString +
                    dumpshares;
        combinations.add(s);
    }

    public BigInteger getAgreedAnswer()
    {
        return agreedAnswerEveryTime;
    }
}
