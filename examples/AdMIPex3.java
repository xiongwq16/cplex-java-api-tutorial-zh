package examples;

/* --------------------------------------------------------------------------
 * File: AdMIPex3.java
 * Version 12.9.0  
 * --------------------------------------------------------------------------
 * Licensed Materials - Property of IBM
 * 5725-A06 5725-A29 5724-Y48 5724-Y49 5724-Y54 5724-Y55 5655-Y21
 * Copyright IBM Corporation 2001, 2019. All Rights Reserved.
 *
 * US Government Users Restricted Rights - Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with
 * IBM Corp.
 * --------------------------------------------------------------------------
 *
 * AdMIPex3.java -  Using the branch callback for optimizing a MIP
 *                  problem with Special Ordered Sets Type 1, with
 *                  all the variables binary
 *
 * To run this example, command line arguments are required.
 * i.e.,   AdMIPex3   filename
 *
 * Example:
 *     java AdMIPex3  example.mps
 */

import ilog.concert.*;
import ilog.cplex.*;
import java.util.Iterator;

public class AdMIPex3 extends Object {
    static double EPS = 1.0e-4;

    public static class SOSbranch extends IloCplex.BranchCallback {
        IloSOS1[] _sos;

        public SOSbranch(IloSOS1[] sos) {
            _sos = sos;
        }

        public void main() throws IloException {
            double bestx = EPS;
            int besti = -1;
            int bestj = -1;
            int num = _sos.length;

            IloNumVar[] var = null;
            double[] x = null;

            for (int i = 0; i < num; ++i) {
                if (getSOSFeasibility(_sos[i]).equals(IloCplex.IntegerFeasibilityStatus.Infeasible)) {
                    var = _sos[i].getNumVars();
                    x = getValues(var);

                    int n = var.length;
                    for (int j = 0; j < n; ++j) {
                        double inf = Math.abs(x[j] - Math.round(x[j]));
                        if (inf > bestx) {
                            bestx = inf;
                            besti = i;
                            bestj = j;
                        }
                    }
                }
            }

            if (besti >= 0) {
                var = _sos[besti].getNumVars();
                int n = var.length;

                IloCplex.BranchDirection[] dir = new IloCplex.BranchDirection[n];
                double[] val = new double[n];

                for (int j = 0; j < n; ++j) {
                    if (j != bestj) {
                        dir[j] = IloCplex.BranchDirection.Down;
                        val[j] = 0.0;
                    } else {
                        dir[j] = IloCplex.BranchDirection.Up;
                        val[j] = 1.0;
                    }
                }
                makeBranch(var, val, dir, getObjValue());
                makeBranch(var[bestj], 0.0, IloCplex.BranchDirection.Down, getObjValue());
            }
        }
    }

    @SuppressWarnings("rawtypes")
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: AdMIPex1 filename");
            System.out.println("   where filename is a file with extension ");
            System.out.println("      MPS, SAV, or LP (lower case is allowed)");
            System.out.println(" Exiting...");
            System.exit(-1);
        }

        try {
            IloCplex cplex = new IloCplex();

            cplex.importModel(args[0]);

            if (cplex.getNSOS1() > 0) {
                IloSOS1[] sos1 = new IloSOS1[cplex.getNSOS1()];
                int i = 0;
                for (Iterator sosit = cplex.SOS1iterator(); sosit.hasNext(); ++i) {
                    sos1[i] = (IloSOS1) sosit.next();
                }
                cplex.use(new SOSbranch(sos1));
                System.out.println("using SOS branch callback for " + sos1.length + " SOS1s");
            }

            cplex.setParam(IloCplex.Param.MIP.Strategy.Search, IloCplex.MIPSearch.Traditional);
            if (cplex.solve()) {
                System.out.println("Solution status = " + cplex.getStatus());
                System.out.println("Solution value  = " + cplex.getObjValue());
            }
            cplex.end();
        } catch (IloException exc) {
            System.err.println("Concert exception caught: " + exc);
        }
    }
}
