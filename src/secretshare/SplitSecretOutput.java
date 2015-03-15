package secretshare;

import java.io.PrintStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * When the secret is split, this is the information that is returned.
 * Note: This object is NOT the "public" information, since the polynomial
 *         used in splitting the secret is in this object.
 *       The "public" information is the '.getShareInfos()' method.
 */
public class SplitSecretOutput
{
    private final PublicInfo publicInfo;
    final List<ShareInfo> sharesInfo = new ArrayList<ShareInfo>();
    private final PolyEquationImpl polynomial;

    public SplitSecretOutput(final PublicInfo inPublicInfo,
                             final PolyEquationImpl inPolynomial)
    {
        publicInfo = inPublicInfo;
        polynomial = inPolynomial;
    }
    public String debugDump()
    {
        String ret = "Public=" + publicInfo.debugDump() + "\n";

        ret += "EQ: " + polynomial.debugDump() + "\n";

        for (ShareInfo share : sharesInfo)
        {
            ret += "SHARE: " + share.debugDump() + "\n";
        }
        return ret;
    }
    public final List<ShareInfo> getShareInfos()
    {
        return Collections.unmodifiableList(sharesInfo);
    }
    public final PublicInfo getPublicInfo()
    {
        return publicInfo;
    }
    public void debugPrintEquationCoefficients(PrintStream out) {
        polynomial.debugPrintEquationCoefficients(out);
    }

	private String dumpshares(List<ShareInfo> usetheseshares)
	{
	    String ret = "";
	    for (ShareInfo share : usetheseshares)
	    {
	        ret += " " + share.getShare();
	    }
	    return ret;
	}


}