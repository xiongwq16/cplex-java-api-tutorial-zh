package examples;

/* --------------------------------------------------------------------------
 * File: Transport.java
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
 */

import ilog.cplex.*;
import ilog.concert.*;

public class Transport {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Transport <type>");
            System.err.println("  type = 0 -> convex  piecewise linear model");
            System.err.println("  type = 1 -> concave piecewise linear model");
            return;
        }

        try {
            IloCplex cplex = new IloCplex();

            int nbDemand = 4;
            int nbSupply = 3;
            double[] supply = { 1000.0, 850.0, 1250.0 };
            double[] demand = { 900.0, 1200.0, 600.0, 400.0 };

            IloNumVar[][] x = new IloNumVar[nbSupply][];
            IloNumVar[][] y = new IloNumVar[nbSupply][];

            for (int i = 0; i < nbSupply; i++) {
                x[i] = cplex.numVarArray(nbDemand, 0., Double.MAX_VALUE);
                y[i] = cplex.numVarArray(nbDemand, 0., Double.MAX_VALUE);
            }

            for (int i = 0; i < nbSupply; i++) // supply must meet demand
                cplex.addEq(cplex.sum(x[i]), supply[i]);

            for (int j = 0; j < nbDemand; j++) { // demand must meet supply
                IloLinearNumExpr v = cplex.linearNumExpr();
                for (int i = 0; i < nbSupply; i++)
                    v.addTerm(1., x[i][j]);
                cplex.addEq(v, demand[j]);
            }

            double[] points;
            double[] slopes;
            if (args[0].charAt(0) == '0') { // convex case
                points = new double[] { 200.0, 400.0 };
                slopes = new double[] { 30.0, 80.0, 130.0 };
            } else { // concave case
                points = new double[] { 200.0, 400.0 };
                slopes = new double[] { 120.0, 80.0, 50.0 };
            }
            for (int i = 0; i < nbSupply; ++i) {
                for (int j = 0; j < nbDemand; ++j) {
                    // 定义中间变量y[i][j]，使用piecewiseLinear进行分段线性函数的处理
                    cplex.addEq(y[i][j], cplex.piecewiseLinear(x[i][j], points, slopes, 0.0, 0.0));
                }
            }

            // 添加构造好的分段线性目标函数
            IloLinearNumExpr expr = cplex.linearNumExpr();
            for (int i = 0; i < nbSupply; ++i) {
                for (int j = 0; j < nbDemand; ++j) {
                    expr.addTerm(y[i][j], 1.);
                }
            }
            cplex.addMinimize(expr);

            if (cplex.solve()) {
                System.out.println("Solution status: " + cplex.getStatus());
                System.out.println(" - Solution: ");
                for (int i = 0; i < nbSupply; ++i) {
                    System.out.print("   " + i + ": ");
                    for (int j = 0; j < nbDemand; ++j)
                        System.out.print("" + cplex.getValue(x[i][j]) + "\t");
                    System.out.println();
                }
                System.out.println("   Cost = " + cplex.getObjValue());
            }
            cplex.end();
        } catch (IloException exc) {
            System.out.println(exc);
        }
    }
}
