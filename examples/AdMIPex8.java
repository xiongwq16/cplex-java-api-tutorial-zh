package examples;
/* -------------------------------------------------------------- -*- Java -*-
 * File: AdMIPex8.java
 * Version 12.9.0  
 * --------------------------------------------------------------------------
 * Licensed Materials - Property of IBM
 * 5725-A06 5725-A29 5724-Y48 5724-Y49 5724-Y54 5724-Y55 5655-Y21
 * Copyright IBM Corporation 2017, 2019. All Rights Reserved.
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

/** AdMIPex8.java -- Solve a facility location problem with cut or lazy
 *                   constraint using the new callback api.
 *
 * Given a set of locations J and a set of clients C, the following model is
 * solved:
 *
 *  Minimize
 *   sum(j in J) fixedCost[j]*opened[j] +
 *   sum(j in J)sum(c in C) cost[c][j]*supply[c][j]
 *  Subject to
 *   sum(j in J) supply[c][j] == 1                    for all c in C
 *   sum(c in C) supply[c][j] <= (|C| - 1) * opened[j]  for all j in J
 *               supply[c][j] in {0, 1}               for all c in C, j in J
 *                    opened[j] in {0, 1}               for all j in J
 *
 * In addition to the constraints stated above, the code also separates
 * a disaggregated version of the capacity constraints (see comments for the
 * cut callback) to improve performance.
 *
 * Optionally, the capacity constraints can be separated from a lazy
 * constraint callback instead of being stated as part of the initial model.
 *
 * See the usage message for how to switch between these options.
 */
public class AdMIPex8 {

   /** Epsilon used for violation of cuts. */
   private static double EPS = 1e-6;

   /** This is the class implementing the callback for facility location.
    *
    * It has three main functions:
    *    - disaggregate: add disagrregated constraints linking clients and
    *      location.
    *    - fromTable: do the same using a cut table.
    *    - lazyCapacity: adds the capacity constraint as a lazy constrain.
    */
   private static class FacilityCallback implements IloCplex.Callback.Function {
      private final IloNumVar[] opened;
      private final IloNumVar[][] supply;
      private final Vector<IloRange> cuts;
      public FacilityCallback(IloNumVar[] opened,
                              IloNumVar[][] supply)
      {
         this.opened = opened;
         this.supply = supply;
         this.cuts = new Vector<IloRange>();
      }

      /** Separate the disaggregated capacity constraints.
       * In the model we have for each location j the constraint
       *    sum(c in clients) supply[c][j] <= (nbClients-1) * opened[j]
       * Clearly, a client can only be serviced from a location that is opened,
       * so we also have a constraint
       *    supply[c][j] <= opened[j]
       * that must be satisfied by every feasible solution. These constraints tend
       * to be violated in LP relaxation. In this callback we separate them.
       */
      private void disaggregate (IloCplex.Callback.Context context) throws IloException {
         final int nbLocations = opened.length;
         final int nbClients = supply.length;
         final IloCplexModeler m = context.getCplex();
      
         // For each j and c check whether in the current solution (obtained by
         // calls to getValue()) we have supply[c][j]>opened[j]. If so, then we have
         // found a violated constraint and add it as a cut.
         for (int j = 0; j < nbLocations; ++j) {
            for (int c = 0; c < nbClients; ++c) {
               final double s = context.getRelaxationPoint(supply[c][j]);
               final double o = context.getRelaxationPoint(opened[j]);
               if ( s > o + EPS) {
                  System.out.println("Adding: " + supply[c][j].getName() + " <= " +
                                     opened[j].getName() + " [" + s + " > " +
                                     o + "]");
                  context.addUserCut(m.le(m.diff(supply[c][j], opened[j]), 0.0),
                                     IloCplex.CutManagement.UseCutPurge, false);
               }
            }
         }
      }
      
      /** Variant of {@link #disaggregate(IloCplex.Callback.Context)} that does
       * not look for violated cuts dynamically.
       * Instead it uses a static table of cuts and scans this table for violated cuts.
       */
      private void cutsFromTable (IloCplex.Callback.Context context) throws IloException {
         for (IloRange cut : cuts) {
            final double lhs = context.getRelaxationValue(cut.getExpr());
            if (lhs < cut.getLB() - EPS || lhs > cut.getUB() + EPS ) {
               System.out.println("Adding: " + cut + " [lhs = " + lhs + "]");
               context.addUserCut(cut, IloCplex.CutManagement.UseCutPurge, false);
            }
         }
      }
      
      /** Function to populate the cut table used by cutsFromTable.
       */
      public void populateCutTable (IloCplexModeler cplex) throws IloException {
         final int nbLocations = opened.length;
         final int nbClients = supply.length;
         // Generate all disaggregated constraints and put them into a
         // table that is scanned by the callback.
         cuts.setSize(0);
         for (int j = 0; j < nbLocations; ++j)
            for (int c = 0; c < nbClients; ++c)
               cuts.add(cplex.le(cplex.diff(supply[c][j], opened[j]), 0.0));
      }

      /** Lazy constraint callback to enforce the capacity constraints.
       * If opened then the callback is invoked for every integer feasible solution
       * CPLEX finds. For each location j it checks whether constraint
       *    sum(c in C) supply[c][j] <= (|C| - 1) * opened[j]
       * is satisfied. If not then it adds the violated constraint as lazy constraint.
       */
      private void lazyCapacity (IloCplex.Callback.Context context) throws IloException {
         final int nbLocations = opened.length;
         final int nbClients = supply.length;
         final IloCplexModeler m = context.getCplex();
         if ( !context.isCandidatePoint() )
            throw new IloException("Unbounded solution");
         for (int j = 0; j < nbLocations; ++j) {
            final double isUsed = context.getCandidatePoint(opened[j]);
            double served = 0.0; // Number of clients currently served from j
            for (int c = 0; c < nbClients; ++c)
               served += context.getCandidatePoint(supply[c][j]);
            if ( served > (nbClients - 1.0) * isUsed + EPS ) {
               IloLinearNumExpr sum = m.linearNumExpr();
               for (int c = 0; c < nbClients; ++c)
                  sum.addTerm(1.0, supply[c][j]);
               sum.addTerm(-(nbClients - 1), opened[j]);
               System.out.println("Adding lazy capacity constraint " + sum +
                                  " <= 0");
               context.rejectCandidate(m.le(sum, 0.0));
            }
         }
      }

      /** This is the function that we have to implement and that CPLEX will call 
       * during the solution process at the places that we asked for.
       */
      @Override
      public void invoke (IloCplex.Callback.Context context) throws IloException {
         if ( context.inRelaxation() ) {
            if ( cuts.size() > 0 ) {
               cutsFromTable(context);
            }
            else {
               disaggregate(context);
            }
         }

         if ( context.inCandidate() ) {
            lazyCapacity (context);
         }
      }
   }

   private static void usage() {
      System.out.println("Usage: java AdMIPex8 [options...]");
      System.out.println(" By default, a user cut callback is used to dynamically");
      System.out.println(" separate constraints.");
      System.out.println();
      System.out.println(" Supported options are:");
      System.out.println("  -table       Instead of the default behavior, use a"     );
      System.out.println("               static table that holds all cuts and"       );
      System.out.println("               scan that table for violated cuts."         );
      System.out.println("  -no-cuts     Do not separate any cuts."                  );
      System.out.println("  -lazy        Do not include capacity constraints in the" );
      System.out.println("               model. Instead, separate them from a lazy"  );
      System.out.println("               constraint callback."                       );
      System.out.println("  -data=<dir>  Specify the directory in which the data"    );
      System.out.println("               file facility.dat is located."              );
      System.exit(2);
   }

   public static void main(String[] args) throws Exception {
      // Set default arguments and parse command line.
      String datadir = "./data";
      boolean fromTable = false;
      boolean lazy = false;
      boolean useCallback = true;

      for (final String arg : args) {
         if ( arg.startsWith("-data=") )
            datadir = arg.substring(6);
         else if ( arg.equals("-table") )
            fromTable = true;
         else if ( arg.equals("-lazy") )
            lazy = true;
         else if ( arg.equals("-no-cuts") )
            useCallback = false;
         else {
            System.out.println("Unknown argument " + arg);
            usage();
         }
      }

      // Setup input file name and use the file.
      InputDataReader reader = new InputDataReader(new File(datadir, "facility.dat").getAbsolutePath());
      double[] fixedCost = reader.readDoubleArray();
      double[][] cost = reader.readDoubleArrayArray();
      int nbLocations = fixedCost.length;
      int nbClients   = cost.length;

      IloCplex cplex = new IloCplex();
      try {
         // Create variables.
         // - opened[j]    If location j is used.
         // - supply[c][j] Amount shipped from location j to client c. This is a
         //                number in [0,1] and specifies the percentage of c's
         //                demand that is served from location i.
         IloNumVar[] opened = cplex.boolVarArray(nbLocations);
         for (int j = 0; j < nbLocations; ++j)
            opened[j].setName("opened(" + j + ")");
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
         if ( !lazy ) {
            for (int j = 0; j < nbLocations; j++) {
               IloLinearNumExpr v = cplex.linearNumExpr();
               for (int c = 0; c < nbClients; c++)
                  v.addTerm(1.0, supply[c][j]);
               cplex.addLe(v, cplex.prod(nbClients - 1, opened[j]));
            }
         }

         // Objective function. We have the fixed cost for useding a location
         // and the cost proportional to the amount that is shipped from a
         // location.
         IloLinearNumExpr obj = cplex.scalProd(fixedCost, opened);
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


         // Now we get to setting up the callback.
         // We instanciate a FacilityCallback and set the wherefrom parameter.
         FacilityCallback fcCallback = new FacilityCallback(opened, supply);
         long wherefrom = 0;
         if ( useCallback ) {
            wherefrom |= IloCplex.Callback.Context.Id.Relaxation;
            if ( fromTable ) {
               fcCallback.populateCutTable(cplex);
            }
         }

         if ( lazy )
            wherefrom |= IloCplex.Callback.Context.Id.Candidate;

         // If wherefrom is not zero we add the callback.
         if ( wherefrom != 0 )
            cplex.use(fcCallback, wherefrom);

         if ( !cplex.solve() )
            throw new RuntimeException("No feasible solution found");
	
         System.out.println("Solution status:                   " +
                            cplex.getStatus());
         System.out.println("Nodes processed:                   " +
                            cplex.getNnodes());
         System.out.println("Active user cuts/lazy constraints: " +
                            cplex.getNcuts(IloCplex.CutType.User));
         double tolerance = cplex.getParam(IloCplex.Param.MIP.Tolerances.Integrality);
         System.out.println("Optimal value:                     " +
                            cplex.getObjValue());
         for (int j = 0; j < nbLocations; j++) {
            if (cplex.getValue(opened[j]) >= 1 - tolerance) {
               System.out.print("Facility " + j + " is used, it serves clients");
               for (int i = 0; i < nbClients; i++) {
                  if (cplex.getValue(supply[i][j]) >= 1 - tolerance)
                     System.out.print(" " + i);
               }
               System.out.println();
            }
         }
      }
      finally {
         cplex.end();
      }
   }
}
