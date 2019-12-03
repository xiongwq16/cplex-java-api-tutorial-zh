package examples;
/* --------------------------------------------------------------------------
 * File: GlobalQPex1.java
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
 * GlobalQPex1.java - Reading in and optimizing a convex or nonconvex (mixed-integer) QP
 * with convex, first order or global optimizer.
 *
 * To run this example, command line arguments are required.
 * That is:   java GlobalQPex1  filename optimalitytarget
 * where 
 *     filename is the name of the file, with .mps, .lp, or .sav extension
 *     optimalitytarget   is the optimality target
 *                 c          for a convex qp or miqp
 *                 f          for a first order solution (only qp)
 *                 g          for the global optimum
 *
 * Example:
 *     java GlobalQPex1  nonconvexqp.lp g
 */

import ilog.concert.*;
import ilog.cplex.*;


public class GlobalQPex1 {
   static void usage() {
      System.out.println("usage:  GlobalQPex1 <filename> <optimality target>");
      System.out.println("          c       convex QP or MIQP");
      System.out.println("          f       first order solution (only QP)");
      System.out.println("          g       global optimum");
   }


   public static void main(String[] args) {
      IloCplex cplex = null;
      try {
         cplex = new IloCplex();
       
         // Evaluate command line option and set optimality target accordingly.
         switch ( args[1].charAt(0) ) {
         case 'c': cplex.setParam(IloCplex.Param.OptimalityTarget,
                                  IloCplex.OptimalityTarget.OptimalConvex);
                   break;
         case 'f': cplex.setParam(IloCplex.Param.OptimalityTarget,
                                  IloCplex.OptimalityTarget.FirstOrder);
                   break;
         case 'g': cplex.setParam(IloCplex.Param.OptimalityTarget,
                                  IloCplex.OptimalityTarget.OptimalGlobal);
                   break;
         default:  usage();
                   return;
         }

         cplex.importModel(args[0]);

         // CPLEX may converge to either local optimum 
         solveAndDisplay(cplex);
      }
      catch (IloCplexModeler.Exception e) {
         if ( args[1].charAt(0) == 'c' &&
              e.getStatus() == 5002      ) {
            /* Status 5002 is CPXERR_Q_NOT_POS_DEF */
           if (cplex.isMIP() == true) {
              System.out.println("Problem is not convex. Use argument g to get global optimum.");
           }
           else {
              System.out.print("Problem is not convex. Use argument f to get local optimum ");
              System.out.println("or g to get global optimum.");
           }
         }
         else if ( args[1].charAt(0) == 'f' &&
              e.getStatus() == 1017         &&
              cplex.isMIP()                   ) {
            /* Status 1017 is CPXERR_NOT_FOR_MIP */
            System.out.print("Problem is a MIP, cannot compute local optima satifying ");
            System.out.println("the first order KKT.");
            System.out.println("Use argument g to get the global optimum.");
         }
         else  {
            System.err.println("Cplex exception '" + e + "' caught");
         }
      }
      catch (IloException e) {
         System.err.println("Concert exception '" + e + "' caught");
      }
      finally {
         if (cplex != null)
            cplex.end();
      }
   }

   static void solveAndDisplay(IloCplex cplex) throws IloException {
      if ( cplex.solve() ) {
         IloLPMatrix lp = (IloLPMatrix)cplex.LPMatrixIterator().next();

         double[] x     = cplex.getValues(lp);
         double[] slack = cplex.getSlacks(lp);

         System.out.println("Solution status = " + cplex.getStatus());
         System.out.println("Solution value  = " + cplex.getObjValue());

         int nvars = x.length;
         for (int j = 0; j < nvars; ++j)
            System.out.println("Variable " + j + ": Value = " + x[j]);

         int ncons = slack.length;
         for (int i = 0; i < ncons; ++i)
            System.out.println("Constraint " + i + ": Slack = " + slack[i]);
      }
   }
}
