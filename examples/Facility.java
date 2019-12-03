package examples;

/* --------------------------------------------------------------------------
 * File: Facility.java
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

import ilog.concert.*;
import ilog.cplex.*;
import java.io.*;

/**
 * Solve a capacitated facility location problem, potentially using Benders
 * decomposition. The model solved here is <code>
 *   minimize
 *       sum(j in locations) fixedCost[j] * open[j] +
 *       sum(j in locations) sum(i in clients) cost[i][j] * supply[i][j]
 *   subject to
 *       sum(j in locations) supply[i][j] == 1                     for each
 *                                                                 client i
 *       sum(i in clients) supply[i][j] <= capacity[j] * open[j]   for each
 *                                                                 location j
 *       supply[i][j] in [0,1]
 *       open[j] in {0, 1}
 * </code> For further details see the {@link usage()} function.
 */
public class Facility {
    static double[] capacity;
    static double[] fixedCost;
    static double[][] cost;

    static int nbLocations;
    static int nbClients;

    /** Dump a usage message and exit with error. */
    static void usage() {
        for (final String line : new String[] { "Usage: java Facility [options] [inputfile]", " where",
                "   inputfile describe a capacitated facility location instance as in",
                "   ../../../examples/data/facility.dat. If no input file",
                "   is specified read the file in example/data directory.", "   Options are:",
                "   -a solve problem with Benders letting CPLEX do the decomposition",
                "   -b solve problem with Benders specifying a decomposition",
                "   -d solve problem without using decomposition (default)", " Exiting..." })
            System.err.println(line);
        System.exit(-1);
    }

    /**
     * Read data from <code>fileName</code> and store it in this class's
     * {@link capacity}, {@link fixedCost}, {@link cost}, {@link nbLocations}, and
     * {@link nbClients} fields.
     * 
     * @param fileName Name of the file to read.
     */
    static void readData(String fileName) throws IOException, InputDataReader.InputDataReaderException {
        System.out.println("Reading data from " + fileName);
        InputDataReader reader = new InputDataReader(fileName);

        fixedCost = reader.readDoubleArray();
        cost = reader.readDoubleArrayArray();
        capacity = reader.readDoubleArray();

        nbLocations = capacity.length;
        nbClients = cost.length;

        // Check consistency of data.
        for (int i = 0; i < nbClients; i++)
            if (cost[i].length != nbLocations)
                throw new IllegalArgumentException("inconsistent data in file " + fileName);
    }

    /** Benders decomposition used for solving the model. */
    private static enum BendersType {
        NO_BENDERS, AUTO_BENDERS, ANNO_BENDERS
    };

    /** Solve capacitated facility location problem. */
    public static void main(String[] args) {
        try {
            // Parse the command line.
            String filename = "./data/facility.dat";
            BendersType benders = BendersType.NO_BENDERS;
            for (final String arg : args) {
                if (arg.startsWith("-")) {
                    if (arg.equals("-a"))
                        benders = BendersType.AUTO_BENDERS;
                    else if (arg.equals("-b"))
                        benders = BendersType.ANNO_BENDERS;
                    else if (arg.equals("-d"))
                        benders = BendersType.NO_BENDERS;
                    else
                        usage();
                } else
                    filename = arg;
            }

            // Read data.
            readData(filename);

            // Create the modeler/solver.
            IloCplex cplex = new IloCplex();

            // Create variables. We have variables
            // open[j] if location j is open.
            // supply[i][j]] how much client i is supplied from location j
            IloNumVar[] open = cplex.boolVarArray(nbLocations);
            IloNumVar[][] supply = new IloNumVar[nbClients][];
            for (int i = 0; i < nbClients; i++)
                supply[i] = cplex.numVarArray(nbLocations, 0.0, 1.0);

            // Constraint: Each client i must be assigned to exactly one location:
            // sum(j in nbLocations) supply[i][j] == 1 for each i in nbClients
            for (int i = 0; i < nbClients; i++)
                cplex.addEq(cplex.sum(supply[i]), 1);
            // Constraint: For each location j, the capacity of the location must
            // be respected:
            // sum(i in nbClients) supply[i][j] <= capacity[j] * open[j]
            for (int j = 0; j < nbLocations; j++) {
                IloLinearNumExpr v = cplex.linearNumExpr();
                for (int i = 0; i < nbClients; i++)
                    v.addTerm(1., supply[i][j]);
                cplex.addLe(v, cplex.prod(capacity[j], open[j]));
            }

            // Objective: Minimize the sum of fixed costs for using a location
            // and the costs for serving a client from a specific location.
            IloLinearNumExpr obj = cplex.scalProd(fixedCost, open);
            for (int i = 0; i < nbClients; i++)
                obj.add(cplex.scalProd(cost[i], supply[i]));
            cplex.addMinimize(obj);

            // Setup Benders decomposition if required.
            switch (benders) {
            case ANNO_BENDERS:
                // We specify the structure for doing a Benders decomposition by
                // telling CPLEX which variables are in the master problem using
                // annotations. By default variables are assigned value
                // CPX_BENDERS_MASTERVALUE+1 and thus go into the workers.
                // Variables open[j] should go into the master and therefore
                // we assign them value CPX_BENDERS_MASTER_VALUE.
                IloCplex.LongAnnotation decomp = cplex.newLongAnnotation(IloCplex.CPX_BENDERS_ANNOTATION,
                        IloCplex.CPX_BENDERS_MASTERVALUE + 1);
                
                // 将open[j]放入主问题中
                for (int j = 0; j < nbLocations; ++j)
                    cplex.setAnnotation(decomp, open[j], IloCplex.CPX_BENDERS_MASTERVALUE);
                cplex.output().println("Solving with explicit Benders decomposition.");
                break;
            case AUTO_BENDERS:
                // Let CPLEX automatically decompose the problem. In the case of
                // a capacitated facility location problem the variables of the
                // master problem should be the integer variables. By setting the
                // Benders strategy parameter to Full, CPLEX will put all integer
                // variables into the master, all continuous varibles into a
                // subproblem, and further decompose that subproblem, if possible.
                cplex.setParam(IloCplex.Param.Benders.Strategy, IloCplex.BendersStrategy.Full);
                cplex.output().println("Solving with automatic Benders decomposition.");
                break;
            case NO_BENDERS:
                cplex.output().println("Solving without Benders decomposition.");
                break;
            }

            // Solve and display solution.
            if (cplex.solve()) {
                cplex.output().println("Solution status: " + cplex.getStatus());
                double tolerance = cplex.getParam(IloCplex.Param.MIP.Tolerances.Integrality);
                cplex.output().println("Optimal value: " + cplex.getObjValue());
                for (int j = 0; j < nbLocations; j++) {
                    if (cplex.getValue(open[j]) >= 1 - tolerance) {
                        cplex.output().print("Facility " + j + " is open, it serves clients");
                        for (int i = 0; i < nbClients; i++)
                            if (cplex.getValue(supply[i][j]) >= 1 - tolerance)
                                cplex.output().print(" " + i);
                        cplex.output().println();
                    }
                }
            }
            cplex.end();
        } catch (IloException exc) {
            System.err.println("Concert exception '" + exc + "' caught");
            System.exit(-1);
        } catch (IOException exc) {
            System.err.println("Error reading file " + args[0] + ": " + exc);
            System.exit(-1);
        } catch (InputDataReader.InputDataReaderException exc) {
            System.err.println(exc);
            System.exit(-1);
        }
    }
}
