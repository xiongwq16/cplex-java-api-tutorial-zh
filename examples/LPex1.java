package examples;
/* --------------------------------------------------------------------------
 * File: LPex1.java
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
 * LPex1.java - Entering and optimizing an LP problem
 *
 * Demonstrates different methods for creating a problem.  The user has to
 * choose the method on the command line:
 *
 *    java LPex1  -r     generates the problem by adding constraints
 *    java LPex1  -c     generates the problem by adding variables
 *    java LPex1  -n     generates the problem by adding expressions
 */

import ilog.concert.*;
import ilog.cplex.*;

public class LPex1 {
    static void usage() {
        System.out.println("usage:   LPex1 <option>");
        System.out.println("options:       -r   build model row by row");
        System.out.println("options:       -c   build model column by column");
        System.out.println("options:       -n   build model nonzero by nonzero");
    }

    public static void main(String[] args) {
        if (args.length != 1 || args[0].charAt(0) != '-') {
            usage();
            return;
        }

        try {
            // Create the modeler/solver object
            IloCplex cplex = new IloCplex();

            IloNumVar[][] var = new IloNumVar[1][];
            IloRange[][] rng = new IloRange[1][];

            // Evaluate command line option and call appropriate populate method.
            // The created ranges and variables are returned as element 0 of arrays
            // var and rng.
            switch (args[0].charAt(1)) {
            case 'r':
                populateByRow(cplex, var, rng);
                break;
            case 'c':
                populateByColumn(cplex, var, rng);
                break;
            case 'n':
                populateByNonzero(cplex, var, rng);
                break;
            default:
                usage();
                return;
            }

            // write model to file
            cplex.exportModel("lpex1.lp");

            // solve the model and display the solution if one was found
            if (cplex.solve()) {
                double[] x = cplex.getValues(var[0]);
                double[] dj = cplex.getReducedCosts(var[0]);
                double[] pi = cplex.getDuals(rng[0]);
                double[] slack = cplex.getSlacks(rng[0]);

                cplex.output().println("Solution status = " + cplex.getStatus());
                cplex.output().println("Solution value  = " + cplex.getObjValue());

                int nvars = x.length;
                for (int j = 0; j < nvars; ++j) {
                    cplex.output().println("Variable " + j + ": Value = " + x[j] + " Reduced cost = " + dj[j]);
                }

                int ncons = slack.length;
                for (int i = 0; i < ncons; ++i) {
                    cplex.output().println("Constraint " + i + ": Slack = " + slack[i] + " Pi = " + pi[i]);
                }
            }
            cplex.end();
        } catch (IloException e) {
            System.err.println("Concert exception '" + e + "' caught");
        }
    }

//     The following methods all populate the problem with data for the following
//     linear program:
//    
//     Maximize
//     x1 + 2 x2 + 3 x3
//     Subject To
//     - x1 + x2 + x3 <= 20
//     x1 - 3 x2 + x3 <= 30
//     Bounds
//     0 <= x1 <= 40
//     End
//    
//     using the IloMPModeler API

    static void populateByRow(IloMPModeler model, IloNumVar[][] var, IloRange[][] rng) throws IloException {
        double[] lb = { 0.0, 0.0, 0.0 };
        double[] ub = { 40.0, Double.MAX_VALUE, Double.MAX_VALUE };
        String[] varname = { "x1", "x2", "x3" };
        IloNumVar[] x = model.numVarArray(3, lb, ub, varname);
        var[0] = x;

        double[] objvals = { 1.0, 2.0, 3.0 };
        model.addMaximize(model.scalProd(x, objvals));

        rng[0] = new IloRange[2];
        rng[0][0] = model.addLe(model.sum(model.prod(-1.0, x[0]),model.prod(1.0, x[1]), model.prod(1.0, x[2])), 
                20.0,
                "c1");
        rng[0][1] = model.addLe(model.sum(model.prod(1.0, x[0]), model.prod(-3.0, x[1]), model.prod(1.0, x[2])), 
                30.0,
                "c2");
    }

    static void populateByColumn(IloMPModeler model, IloNumVar[][] var, IloRange[][] rng) throws IloException {
        IloObjective obj = model.addMaximize();

        rng[0] = new IloRange[2];
        rng[0][0] = model.addRange(-Double.MAX_VALUE, 20.0, "c1");
        rng[0][1] = model.addRange(-Double.MAX_VALUE, 30.0, "c2");

        IloRange r0 = rng[0][0];
        IloRange r1 = rng[0][1];

        var[0] = new IloNumVar[3];
        var[0][0] = model.numVar(model.column(obj, 1.0).and(model.column(r0, -1.0).and(model.column(r1, 1.0))), 0.0,
                40.0, "x1");
        var[0][1] = model.numVar(model.column(obj, 2.0).and(model.column(r0, 1.0).and(model.column(r1, -3.0))), 0.0,
                Double.MAX_VALUE, "x2");
        var[0][2] = model.numVar(model.column(obj, 3.0).and(model.column(r0, 1.0).and(model.column(r1, 1.0))), 0.0,
                Double.MAX_VALUE, "x3");
    }

    static void populateByNonzero(IloMPModeler model, IloNumVar[][] var, IloRange[][] rng) throws IloException {
        double[] lb = { 0.0, 0.0, 0.0 };
        double[] ub = { 40.0, Double.MAX_VALUE, Double.MAX_VALUE };
        IloNumVar[] x = model.numVarArray(3, lb, ub);
        var[0] = x;

        double[] objvals = { 1.0, 2.0, 3.0 };
        model.add(model.maximize(model.scalProd(x, objvals)));

        rng[0] = new IloRange[2];
        rng[0][0] = model.addRange(-Double.MAX_VALUE, 20.0);
        rng[0][1] = model.addRange(-Double.MAX_VALUE, 30.0);

        rng[0][0].setExpr(model.sum(model.prod(-1.0, x[0]), model.prod(1.0, x[1]), model.prod(1.0, x[2])));
        rng[0][1].setExpr(model.sum(model.prod(1.0, x[0]), model.prod(-3.0, x[1]), model.prod(1.0, x[2])));

        x[0].setName("x1");
        x[1].setName("x2");
        x[2].setName("x3");
        rng[0][0].setName("c1");
        rng[0][1].setName("c2");
    }
}
