package examples;

/* --------------------------------------------------------------------------
 * File: Populate.java
 * Version 12.9.0  
 * --------------------------------------------------------------------------
 * Licensed Materials - Property of IBM
 * 5725-A06 5725-A29 5724-Y48 5724-Y49 5724-Y54 5724-Y55 5655-Y21
 * Copyright IBM Corporation 2007, 2019. All Rights Reserved.
 *
 * US Government Users Restricted Rights - Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with
 * IBM Corp.
 * --------------------------------------------------------------------------
 *
 * Populate.java - Reading in and generating multiple solutions to a MIP
 *                 problem.
 *
 * To run this example, command line arguments are required.
 * i.e.,   java Populate  filename
 * where 
 *     filename is the name of the file, with .mps, .lp, or .sav extension
 * Example:
 *     java Populate  location.lp
 */

import ilog.concert.*;
import ilog.cplex.*;

public class Populate {
    static final double EPSZERO = 1.0E-10;

    static void usage() {
        System.out.println("usage:  Populate <filename>");
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            usage();
            return;
        }
        try {
            IloCplex cplex = new IloCplex();

            cplex.importModel(args[0]);

            /*
             * Set the solution pool relative gap parameter to obtain solutions of objective
             * value within 10% of the optimal
             */
            // 针对解法池中的解法设置目标值的相对容错。 根据此度量比现任解法更差（大于最小化或者小于最大化）的解法不会保留在解法池中。
            // 例如，如果将此参数设置为 0.01，那么将丢弃比现任差 1% 或更多的解法。
            cplex.setParam(IloCplex.Param.MIP.Pool.RelGap, 0.1);

            if (cplex.populate()) {
                System.out.println("Solution status = " + cplex.getStatus());
                System.out.println("Incumbent objective value  = " + cplex.getObjValue());

                // Access the IloLPMatrix object that has been read from a file in
                // order to access variables which are the columns of the LP. The
                // method importModel() guarantees that exactly one IloLPMatrix
                // object will exist, which is why no tests or iterators are
                // needed in the following line of code.

                IloLPMatrix lp = (IloLPMatrix) cplex.LPMatrixIterator().next();

                double[] incx = cplex.getValues(lp);
                for (int j = 0; j < incx.length; j++) {
                    System.out.println("Variable " + j + ": Value = " + incx[j]);
                }
                System.out.println();

                /* Get the number of solutions in the solution pool */

                int numsol = cplex.getSolnPoolNsolns();
                System.out.println("The solution pool contains " + numsol + " solutions.");

                /*
                 * Some solutions are deleted from the pool because of the solution pool
                 * relative gap parameter
                 */

                int numsolreplaced = cplex.getSolnPoolNreplaced();
                System.out.println(numsolreplaced + " solutions were removed due to the "
                        + "solution pool relative gap parameter.");

                System.out.println("In total, " + (numsol + numsolreplaced) + " solutions were generated.");

                /*
                 * Get the average objective value of solutions in the solution pool
                 */

                System.out.println(
                        "The average objective value of the solutions is " + cplex.getSolnPoolMeanObjValue() + ".");
                System.out.println();

                /*
                 * Write out the objective value of each solution and its difference to the
                 * incumbent
                 */

                for (int i = 0; i < numsol; i++) {

                    double[] x = cplex.getValues(lp, i);

                    /*
                     * Compute the number of variables that differ in the solution and in the
                     * incumbent
                     */

                    int numdiff = 0;
                    for (int j = 0; j < x.length; j++) {
                        if (Math.abs(x[j] - incx[j]) > EPSZERO)
                            numdiff++;
                    }

                    System.out.println("Solution " + i + " with objective " + cplex.getObjValue(i) + " differs in "
                            + numdiff + " of " + x.length + " variables.");
                }
            }
            cplex.end();
        } catch (IloException e) {
            System.err.println("Concert exception caught: " + e);
        }
    }
}
