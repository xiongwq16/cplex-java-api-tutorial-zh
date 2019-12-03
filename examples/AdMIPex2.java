package examples;

/* --------------------------------------------------------------------------
 * File: AdMIPex2.java
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
 * AdMIPex2.java -  Use the heuristic callback for optimizing a MIP problem
 *
 * To run this example, command line arguments are required.
 * i.e.,   java AdMIPex2   filename
 *
 * Example:
 *     java AdMIPex2  example.mps
 */

import ilog.concert.*;
import ilog.cplex.*;

public class AdMIPex2 {
    static class RoundDown extends IloCplex.HeuristicCallback {
        IloNumVar[] _vars;

        RoundDown(IloNumVar[] vars) {
            _vars = vars;
        }

        public void main() throws IloException {
            double[] obj = getObjCoefs(_vars);
            double[] x = getValues(_vars);
            IloCplex.IntegerFeasibilityStatus[] feas = getFeasibilities(_vars);

            double objval = getObjValue();
            int cols = _vars.length;
            for (int j = 0; j < cols; j++) {
                // Set the fractional variable to zero and update the objective value
                if (feas[j].equals(IloCplex.IntegerFeasibilityStatus.Infeasible)) {
                    objval -= x[j] * obj[j];
                    x[j] = 0.0;
                }
            }
            setSolution(_vars, x, objval);
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: AdMIPex2 filename");
            System.out.println("   where filename is a file with extension ");
            System.out.println("      MPS, SAV, or LP (lower case is allowed)");
            System.out.println(" Exiting...");
            System.exit(-1);
        }

        try {
            IloCplex cplex = new IloCplex();

            cplex.importModel(args[0]);

            // Check model is all binary except for objective constant variable
            if (cplex.getNbinVars() < cplex.getNcols() - 1) {
                System.err.println("Problem contains non-binary variables, exiting.");
                System.exit(-1);
            }
            
            // 获取问题矩阵
            IloLPMatrix lp = (IloLPMatrix) cplex.LPMatrixIterator().next();

            cplex.use(new RoundDown(lp.getNumVars()));

            cplex.setParam(IloCplex.Param.MIP.Strategy.Search, IloCplex.MIPSearch.Traditional);
            if (cplex.solve()) {
                System.out.println("Solution status = " + cplex.getStatus());
                System.out.println("Solution value  = " + cplex.getObjValue());
            }
            cplex.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
        }
    }
}
