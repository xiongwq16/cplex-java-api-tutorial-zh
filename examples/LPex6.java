package examples;
/* --------------------------------------------------------------------------
 * File: LPex6.java
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
 * LPex6.java - Illustrates that optimal basis can be copied and
 *              used to start an optimization.
 */
 
import ilog.concert.*;
import ilog.cplex.*;


public class LPex6 {
   public static void main(String[] args) {
      try {
         IloCplex cplex = new IloCplex();
       
         IloNumVar[][] var = new IloNumVar[1][];
         IloRange[][]  rng = new IloRange[1][];
         
         populateByRow(cplex, var, rng);
       
         IloCplex.BasisStatus[] cstat = {
            IloCplex.BasisStatus.AtUpper,
            IloCplex.BasisStatus.Basic,
            IloCplex.BasisStatus.Basic
         };
         IloCplex.BasisStatus[] rstat = {
            IloCplex.BasisStatus.AtLower,
            IloCplex.BasisStatus.AtLower
         };
         
         
         cplex.setBasisStatuses(var[0], cstat, rng[0], rstat);
         
         if ( cplex.solve() ) {
            System.out.println("Solution status = " + cplex.getStatus());
            System.out.println("Solution value  = " + cplex.getObjValue());
            System.out.println("Iteration count = " + cplex.getNiterations64());
          
            double[] x     = cplex.getValues(var[0]);
            double[] dj    = cplex.getReducedCosts(var[0]);
            double[] pi    = cplex.getDuals(rng[0]);
            double[] slack = cplex.getSlacks(rng[0]);
          
            int nvars = x.length;
            for (int j = 0; j < nvars; ++j) {
               System.out.println("Variable " + j +
                                  ": Value = " + x[j] +
                                  " Reduced cost = " + dj[j]);
            }
          
            int ncons = slack.length;
            for (int i = 0; i < ncons; ++i) {
               System.out.println("Constraint " + i +
                                  ": Slack = " + slack[i] +
                                  " Pi = " + pi[i]);
            }
         }
         cplex.end();
      }
      catch (IloException exc) {
        System.err.println("Concert exception '" + exc + "' caught");
      }
   }

   static void populateByRow(IloMPModeler  model,
                             IloNumVar[][] var,
                             IloRange[][]  rng) throws IloException {
      double[] lb = {0.0, 0.0, 0.0};
      double[] ub = {40.0, Double.MAX_VALUE, Double.MAX_VALUE};
      var[0] = model.numVarArray(3, lb, ub);
    
      double[] objvals = {1.0, 2.0, 3.0};
      model.addMaximize(model.scalProd(var[0], objvals));
    
      rng[0] = new IloRange[2];
      rng[0][0] = model.addLe(model.sum(model.prod(-1.0, var[0][0]),
                                        model.prod( 1.0, var[0][1]),
                                        model.prod( 1.0, var[0][2])), 20.0);
      rng[0][1] = model.addLe(model.sum(model.prod( 1.0, var[0][0]),
                                        model.prod(-3.0, var[0][1]),
                                        model.prod( 1.0, var[0][2])), 30.0);
   }
}
