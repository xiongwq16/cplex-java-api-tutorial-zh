package examples;


/* --------------------------------------------------------------------------
 * File: AdMIPex5.java
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
import java.io.File;
import java.util.Vector;

/**
 * Solve a facility location problem with cut callbacks or lazy constraints.
 *
 * Given a set of locations J and a set of clients C, the following model is
 * solved:
 *
 * Minimize sum(j in J) fixedCost[j]*used[j] + sum(j in J)sum(c in C)
 * cost[c][j]*supply[c][j] Subject to sum(j in J) supply[c][j] == 1 for all c in
 * C sum(c in C) supply[c][j] <= (|C| - 1) * used[j] for all j in J supply[c][j]
 * in {0, 1} for all c in C, j in J used[j] in {0, 1} for all j in J
 *
 * In addition to the constraints stated above, the code also separates a
 * disaggregated version of the capacity constraints (see comments for the cut
 * callback) to improve performance.
 *
 * Optionally, the capacity constraints can be separated from a lazy constraint
 * callback instead of being stated as part of the initial model.
 *
 * See the usage message for how to switch between these options.
 */
public class AdMIPex5 {
    /** Epsilon used for violation of cuts. */
    private static double EPS = 1.0e-6;

    /**
     * User cut callback to separate the disaggregated capacity constraints.
     *
     * In the model we have for each location j the constraint sum(c in clients)
     * supply[c][j] <= (nbClients-1) * used[j] Clearly, a client can only be
     * serviced from a location that is used, so we also have a constraint
     * supply[c][j] <= used[j] that must be satisfied by every feasible solution.
     * These constraints tend to be violated in LP relaxation. In this callback we
     * separate them.
     */
    public static class Disaggregated extends IloCplex.UserCutCallback {
        private final IloModeler modeler;
        private final IloNumVar[] used;
        private final IloNumVar[][] supply;

        public Disaggregated(IloModeler modeler, IloNumVar[] used, IloNumVar[][] supply) {
            this.modeler = modeler;
            this.used = used;
            this.supply = supply;
        }

        /**
         * Separate cuts.
         *
         * CPLEX invokes this callback when separating cuts at search tree nodes
         * (including the root node). The current fractional solution can be obtained
         * via {@link #getValue(IloNumVar)} and {@link #getValues(IloNumVar[])}.
         * Separated cuts are added via {@link #add(IloRange,int)} or
         * {@link #addLocal(IloRange)}.
         */
        public void main() throws IloException {
            int nbLocations = used.length;
            int nbClients = supply.length;

            // For each j and c check whether in the current solution (obtained by
            // calls to getValue()) we have supply[c][j]>used[j]. If so, then we have
            // found a violated constraint and add it as a cut.
            for (int j = 0; j < nbLocations; ++j) {
                for (int c = 0; c < nbClients; ++c) {
                    double s = getValue(supply[c][j]);
                    double o = getValue(used[j]);
                    if (s > o + EPS) {
                        System.out.println(
                                "Adding: %s <= %s [%f > %f]".format(supply[c][j].getName(), used[j].getName(), s, o));
                        add(modeler.le(modeler.diff(supply[c][j], used[j]), 0.0), IloCplex.CutManagement.UseCutPurge);
                    }
                }
            }
        }
    }

    /**
     * Variant of the Disaggregated callback that does not look for violated cuts
     * dynamically. Instead it uses a static table of cuts and scans this table for
     * violated cuts.
     */
    public static class CutsFromTable extends IloCplex.UserCutCallback {
        private final Vector<IloRange> cuts;

        public CutsFromTable(Vector<IloRange> cuts) {
            this.cuts = cuts;
        }

        public void main() throws IloException {
            for (final IloRange cut : cuts) {
                double lhs = getValue(cut.getExpr());
                if (lhs < cut.getLB() - EPS || lhs > cut.getUB() + EPS) {
                    System.out.println("Adding %s [lhs=%f]".format(cut.toString(), lhs));
                }
            }
        }
    }

    /**
     * Lazy constraint callback to enforce the capacity constraints.
     *
     * If used then the callback is invoked for every integer feasible solution
     * CPLEX finds. For each location j it checks whether constraint sum(c in C)
     * supply[c][j] <= (|C| - 1) * used[j] is satisfied. If not then it adds the
     * violated constraint as lazy constraint.
     */
    public static class LazyCallback extends IloCplex.LazyConstraintCallback {
        private final IloModeler modeler;
        private final IloNumVar[] used;
        private final IloNumVar[][] supply;
        
        public LazyCallback(IloModeler modeler, IloNumVar[] used, IloNumVar[][] supply) {
            this.modeler = modeler;
            this.used = used;
            this.supply = supply;
        }

        public void main() throws IloException {
            int nbLocations = used.length;
            int nbClients = supply.length;
            for (int j = 0; j < nbLocations; ++j) {
                double isused = getValue(used[j]);
                double served = 0.0; // Number of clients currently served from j
                for (int c = 0; c < nbClients; ++c)
                    served += getValue(supply[c][j]);
                if (served > (nbClients - 1.0) * isused + EPS) {
                    IloLinearNumExpr sum = modeler.linearNumExpr();
                    for (int c = 0; c < nbClients; ++c)
                        sum.addTerm(1.0, supply[c][j]);
                    sum.addTerm(-(nbClients - 1), used[j]);
                    System.out.println("Adding lazy capacity constraint %s <= 0".format(sum.toString()));
                    add(modeler.le(sum, 0.0));
                }
            }
        }
    }

    private static void usage() {
        System.out.println("Usage: java AdMIPex5 [options...]");
        System.out.println(" By default, a user cut callback is used to dynamically");
        System.out.println(" separate constraints.");
        System.out.println();
        System.out.println(" Supported options are:");
        System.out.println("  -table       Instead of the default behavior, use a");
        System.out.println("               static table that holds all cuts and");
        System.out.println("               scan that table for violated cuts.");
        System.out.println("  -no-cuts     Do not separate any cuts.");
        System.out.println("  -lazy        Do not include capacity constraints in the");
        System.out.println("               model. Instead, separate them from a lazy");
        System.out.println("               constraint callback.");
        System.out.println("  -data=<dir>  Specify the directory in which the data");
        System.out.println("               file facility.dat is located.");
        System.exit(2);
    }

    public static void main(String[] args) throws Exception {
        // Set default arguments and parse command line.
        String datadir = "./data";
        boolean fromTable = false;
        boolean lazy = false;
        boolean useCallback = true;

        for (final String arg : args) {
            if (arg.startsWith("-data="))
                datadir = arg.substring(6);
            else if (arg.equals("-table"))
                fromTable = true;
            else if (arg.equals("-lazy"))
                lazy = true;
            else if (arg.equals("-no-cuts"))
                useCallback = false;
            else {
                System.out.println("Unknown argument " + arg);
                usage();
            }
        }

        // Setup input file name and used the file.
        InputDataReader reader = new InputDataReader(new File(datadir, "facility.dat").getAbsolutePath());
        double[] fixedCost = reader.readDoubleArray();
        double[][] cost = reader.readDoubleArrayArray();
        int nbLocations = fixedCost.length;
        int nbClients = cost.length;

        IloCplex cplex = new IloCplex();
        try {
            // Create variables.
            // - used[j] If location j is used.
            // - supply[c][j] Amount shipped from location j to client c. This is a
            // number in [0,1] and specifies the percentage of c's
            // demand that is served from location i.
            IloNumVar[] used = cplex.boolVarArray(nbLocations);
            for (int j = 0; j < nbLocations; ++j)
                used[j].setName("used(" + j + ")");
            IloNumVar[][] supply = new IloNumVar[nbClients][];
            for (int c = 0; c < nbClients; c++) {
                supply[c] = cplex.boolVarArray(nbLocations);
                for (int j = 0; j < nbLocations; ++j)
                    supply[c][j].setName("supply(" + c + ")(" + j + ")");
            }

            // The supply for each client must sum to 1, i.e., the demand of each
            // client must be met.
            for (int c = 0; c < nbClients; c++)
                cplex.addEq(cplex.sum(supply[c], 0, supply[c].length), 1);

            // Capacity constraint for each location. We just require that a single
            // location cannot serve all clients, that is, the capacity of each
            // location is nbClients-1. This makes the model a little harder to
            // solve and allows us to separate more cuts.
            if (!lazy) {
                for (int j = 0; j < nbLocations; j++) {
                    IloLinearNumExpr v = cplex.linearNumExpr();
                    for (int c = 0; c < nbClients; c++)
                        v.addTerm(1.0, supply[c][j]);
                    cplex.addLe(v, cplex.prod(nbClients - 1, used[j]));
                }
            }

            // Objective function. We have the fixed cost for useding a location
            // and the cost proportional to the amount that is shipped from a
            // location.
            IloLinearNumExpr obj = cplex.scalProd(fixedCost, used);
            for (int c = 0; c < nbClients; c++) {
                obj.add(cplex.scalProd(cost[c], supply[c]));
            }
            cplex.addMinimize(obj);

            // Tweak some CPLEX parameters so that CPLEX has a harder time to
            // solve the model and our cut separators can actually kick in.
            cplex.setParam(IloCplex.Param.Threads, 1);
            cplex.setParam(IloCplex.Param.MIP.Strategy.HeuristicFreq, -1);
            cplex.setParam(IloCplex.Param.MIP.Cuts.MIRCut, -1);
            cplex.setParam(IloCplex.Param.MIP.Cuts.Implied, -1);
            cplex.setParam(IloCplex.Param.MIP.Cuts.Gomory, -1);
            cplex.setParam(IloCplex.Param.MIP.Cuts.FlowCovers, -1);
            cplex.setParam(IloCplex.Param.MIP.Cuts.PathCut, -1);
            cplex.setParam(IloCplex.Param.MIP.Cuts.LiftProj, -1);
            cplex.setParam(IloCplex.Param.MIP.Cuts.ZeroHalfCut, -1);
            cplex.setParam(IloCplex.Param.MIP.Cuts.Cliques, -1);
            cplex.setParam(IloCplex.Param.MIP.Cuts.Covers, -1);

            if (useCallback) {
                if (fromTable) {
                    // Generate all disaggregated constraints and put them into a
                    // table that is scanned by the callback.
                    Vector<IloRange> cuts = new Vector<IloRange>();
                    for (int j = 0; j < nbLocations; ++j)
                        for (int c = 0; c < nbClients; ++c)
                            cuts.add(cplex.le(cplex.diff(supply[c][j], used[j]), 0.0));
                    cplex.use(new CutsFromTable(cuts));
                } else {
                    cplex.use(new Disaggregated(cplex, used, supply));
                }
            }
            if (lazy)
                cplex.use(new LazyCallback(cplex, used, supply));

            if (!cplex.solve())
                throw new RuntimeException("No feasible solution found");

            System.out.println("Solution status:                   " + cplex.getStatus());
            System.out.println("Nodes processed:                   " + cplex.getNnodes());
            System.out.println("Active user cuts/lazy constraints: " + cplex.getNcuts(IloCplex.CutType.User));
            double tolerance = cplex.getParam(IloCplex.Param.MIP.Tolerances.Integrality);
            System.out.println("Optimal value:                     " + cplex.getObjValue());
            for (int j = 0; j < nbLocations; j++) {
                if (cplex.getValue(used[j]) >= 1 - tolerance) {
                    System.out.print("Facility " + j + " is used, it serves clients");
                    for (int i = 0; i < nbClients; i++) {
                        if (cplex.getValue(supply[i][j]) >= 1 - tolerance)
                            System.out.print(" " + i);
                    }
                    System.out.println();
                }
            }
        } finally {
            cplex.end();
        }
    }
}
