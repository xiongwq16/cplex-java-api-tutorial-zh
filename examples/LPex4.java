package examples;

/* --------------------------------------------------------------------------
 * File: LPex4.java
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
 * LPex4.java - Illustrating the CPLEX callback functionality.
 * 
 * This is a modification of LPex1.java, where we use a callback
 * function to print the iteration info, rather than have CPLEX
 * do it.   Note that the actual LP that is solved is slightly
 * different to make the output more interesting.
 */

import ilog.concert.*;
import ilog.cplex.*;

public class LPex4 {
    // 会改变每一代的输出内容
    // Implement the callback as an extension of class
    // IloCplex.ContinuousCallback by overloading method main(). In the
    // implementation use protected methods of class IloCplex.ContinuousCallback
    // and its super classes, such as getNiterations64(), isFeasible(),
    // getObjValue(), and getInfeasibility() used in this example.
    static class MyCallback extends IloCplex.ContinuousCallback {
        public void main() throws IloException {
            System.out.print("Iteration " + getNiterations64() + ": ");
            if (isFeasible())
                System.out.println("Objective = " + getObjValue());
            else
                System.out.println("Infeasibility measure = " + getInfeasibility());
        }
    }

    public static void main(String[] args) {
        try {
            IloCplex cplex = new IloCplex();
            IloLPMatrix lp = populateByRow(cplex);

            // turn off presolve to prevent it from completely solving the model
            // before entering the actual LP optimizer
            cplex.setParam(IloCplex.Param.Preprocessing.Presolve, false);

            // turn off logging
            cplex.setOut(null);

            // create and instruct cplex to use callback
            cplex.use(new MyCallback());

            if (cplex.solve()) {
                double[] x = cplex.getValues(lp);
                double[] dj = cplex.getReducedCosts(lp);
                double[] pi = cplex.getDuals(lp);
                double[] slack = cplex.getSlacks(lp);

                System.out.println("Solution status = " + cplex.getStatus());
                System.out.println("Iterations      = " + cplex.getNiterations64());
                System.out.println("Solution value  = " + cplex.getObjValue());

                int nvars = x.length;
                for (int j = 0; j < nvars; ++j) {
                    System.out.println("Variable " + j + ": Value = " + x[j] + " Reduced cost = " + dj[j]);
                }

                int ncons = slack.length;
                for (int i = 0; i < ncons; ++i) {
                    System.out.println("Constraint " + i + ": Slack = " + slack[i] + " Pi = " + pi[i]);
                }
            }
            cplex.end();
        } catch (IloException e) {
            System.err.println("Concert exception '" + e + "' caught");
        }
    }

    static IloLPMatrix populateByRow(IloMPModeler model) throws IloException {
        IloLPMatrix lp = model.addLPMatrix();

        double[] lb = { 0.0, 0.0, 0.0 };
        double[] ub = { 40.0, Double.MAX_VALUE, Double.MAX_VALUE };
        IloNumVar[] x = model.numVarArray(model.columnArray(lp, 3), lb, ub);

        double[] lhs = { -Double.MAX_VALUE, -Double.MAX_VALUE };
        double[] rhs = { 20.0, 30.0 };
        double[][] val = { { -1.0, 1.0, 1.0 }, { 1.0, -3.0, 1.0 } };
        int[][] ind = { { 0, 1, 2 }, { 0, 1, 2 } };
        lp.addRows(lhs, rhs, ind, val);

        double[] objvals = { 1.0, 2.0, 3.0 };
        model.addMaximize(model.scalProd(x, objvals));

        return (lp);
    }
}
