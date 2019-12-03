package examples;
/* --------------------------------------------------------------------------
 * File: QCPDual.java
 * Version 12.9.0
 * --------------------------------------------------------------------------
 * Licensed Materials - Property of IBM
 * 5725-A06 5725-A29 5724-Y48 5724-Y49 5724-Y54 5724-Y55 5655-Y21
 * Copyright IBM Corporation 2003, 2019. All Rights Reserved.
 *
 * US Government Users Restricted Rights - Use, duplication or
 * disclosure restricted by GSA ADP Schedule Contract with
 * IBM Corp.
 * --------------------------------------------------------------------------
 */

/*
 * QCPDual.java - Illustrates how to query and analyze dual values of QCPs
 */
import ilog.cplex.*;
import ilog.concert.*;
import java.util.HashMap;

public final class QCPDual {
   /** Tolerance applied when testing values for zero. */
   public static final double ZEROTOL = 1e-6;

   /** Create a string representation of an array.
    * This is similar to {@java.util.Arrays#toString(double[])} but
    * uses a different output format for the individual elements.
    */
   private static final String arrayToString(double[] array) {
      final StringBuffer result = new StringBuffer();
      result.append("[");
      for (int i = 0; i < array.length; ++i)
         result.append(String.format(" %7.3f", array[i]));
      result.append(" ]");
      return result.toString();
   }

   /* ***************************************************************** *
    *                                                                   *
    *    C A L C U L A T E   D U A L S   F O R   Q U A D S              *
    *                                                                   *
    *   CPLEX does not give us the dual multipliers for quadratic       *
    *   constraints directly. This is because they may not be properly  *
    *   defined at the cone top and deciding whether we are at the cone *
    *   top or not involves (problem specific) tolerance issues. CPLEX  *
    *   instead gives us all the values we need in order to compute the *
    *   dual multipliers if we are not at the cone top.                 *
    *                                                                   *
    * ***************************************************************** */
      
   /** Calculate dual multipliers for quadratic constraints from dual
    * slack vectors and optimal solutions.
    * The dual multiplier is essentially the dual slack divided
    * by the derivative evaluated at the optimal solution. If the optimal
    * solution is 0 then the derivative at this point is not defined (we are
    * at the cone top) and we cannot compute a dual multiplier.
    * @param cplex The IloCplex instance that holds the optimal solution.
    * @param xval  The optimal solution vector.
    * @param tol   The tolerance used to decide whether we are at the cone
    *              top or not.
    * @param x     Array with all variables in the model.
    * @param qs    Array of quadratic constraints for which duals shall
    *              be calculated.
    * @return An array with dual multipliers for all quadratic
    *         constraints in <code>qs</code>.
    * @throw IloException if querying data from <code>cplex</code> fails.
    * @throw RuntimeException if the optimal solution is at the cone top.
    */
   private static double[] getqconstrmultipliers(IloCplex cplex, double[] xval, double tol, IloNumVar[] x, IloRange[] qs) throws IloException
   {
      // Store solution vector in hash map so that lookup is easy.
      final HashMap<IloNumVar,Double> sol = new HashMap<IloNumVar,Double>();
      for (int j = 0; j < x.length; ++j)
         sol.put(x[j], xval[j]);

      final double[] qpi = new double[qs.length];
      for (int i = 0; i < qs.length; ++i) {
         // Turn the dual slack vector in a map so that lookup is easy.
         final HashMap<IloNumVar,Double> dslack = new HashMap<IloNumVar,Double>();
         for (IloLinearNumExprIterator it = cplex.getQCDSlack(qs[i]).linearIterator(); it.hasNext(); /* nothing */) {
            IloNumVar v = it.nextNumVar();
            dslack.put(v, it.getValue());
         }

         // Sparse vector for gradient.
         final HashMap<IloNumVar,Double> grad = new HashMap<IloNumVar,Double>();
         boolean conetop = true;
         for (IloQuadNumExprIterator it = ((IloLQNumExpr)qs[i].getExpr()).quadIterator();
              it.hasNext(); /* nothing */)
         {
            it.next();
            IloNumVar x1 = it.getNumVar1();
            IloNumVar x2 = it.getNumVar2();
            if ( sol.get(x1) > tol || sol.get(x2) > tol )
               conetop = false;
            final Double oldx1 = grad.get(x1);
            grad.put(x1, (oldx1 != null ? oldx1.doubleValue() : 0.0) + sol.get(x2) * it.getValue());
            final Double oldx2 = grad.get(x2);
            grad.put(x2, (oldx2 != null ? oldx2.doubleValue() : 0.0) + sol.get(x1) * it.getValue());
         }
         if ( conetop )
            throw new RuntimeException("Cannot compute dual multiplier at cone top!");

         // Compute qpi[i] as slack/gradient.
         // We may have several indices to choose from and use the one
         // with largest absolute value in the denominator.
         boolean ok = false;
         double maxabs = -1.0;
         for (int j = 0; j < x.length; ++j) {
            if (grad.containsKey(x[j])) {
               final double g = grad.get(x[j]);
               if ( Math.abs(g) > tol ) {
                  if ( Math.abs(g) > maxabs ) {
                     final Double d = dslack.get(x[j]);
                     qpi[i] = (d != null ? d.doubleValue() : 0.0) / g;
                     maxabs = Math.abs(g);
                  }
                  ok = true;
               }
            }
         }
         if ( !ok ) {
            // Dual slack is all 0. qpi[i] can be anything, just set to 0.
            qpi[i] = 0.0;
         }
      }

      return qpi;
   }

   /** The example's main function. */
   public static void main(String[] args) {
      IloCplex cplex = null;
      try {
         cplex = new IloCplex();

         /* ***************************************************************** *
          *                                                                   *
          *    S E T U P   P R O B L E M                                      *
          *                                                                   *
          *  We create the following problem:                                 *
          * Minimize                                                          *
          *  obj: 3x1 - x2 + 3x3 + 2x4 + x5 + 2x6 + 4x7                       *
          * Subject To                                                        *
          *  c1: x1 + x2 = 4                                                  *
          *  c2: x1 + x3 >= 3                                                 *
          *  c3: x6 + x7 <= 5                                                 *
          *  c4: -x1 + x7 >= -2                                               *
          *  q1: [ -x1^2 + x2^2 ] <= 0                                        *
          *  q2: [ 4.25x3^2 -2x3*x4 + 4.25x4^2 - 2x4*x5 + 4x5^2  ] + 2 x1 <= 9.0
          *  q3: [ x6^2 - x7^2 ] >= 4                                         *
          * Bounds                                                            *
          *  0 <= x1 <= 3                                                     *
          *  x2 Free                                                          *
          *  0 <= x3 <= 0.5                                                   *
          *  x4 Free                                                          *
          *  x5 Free                                                          *
          *  x7 Free                                                          *
          * End                                                               *
          *                                                                   *
          * ***************************************************************** */

         IloNumVar[] x = new IloNumVar[7];
         x[0] = cplex.numVar(0, 3, "x1");
         x[1] = cplex.numVar(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "x2");
         x[2] = cplex.numVar(0.0, 0.5, "x3");
         x[3] = cplex.numVar(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "x4");
         x[4] = cplex.numVar(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "x5");
         x[5] = cplex.numVar(0.0, Double.POSITIVE_INFINITY, "x6");
         x[6] = cplex.numVar(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, "x7");

         IloRange[] linear = new IloRange[4];
         linear[0] = cplex.addEq(cplex.sum(x[0], x[1]), 4.0, "c1");
         linear[1] = cplex.addGe(cplex.sum(x[0], x[2]), 3.0, "c2");
         linear[2] = cplex.addLe(cplex.sum(x[5], x[6]), 5.0, "c3");
         linear[3] = cplex.addGe(cplex.diff(x[6], x[0]), -2.0, "c4");

         IloRange[] quad = new IloRange[3];
         quad[0] = cplex.addLe(cplex.sum(cplex.prod(-1, x[0], x[0]),
                                         cplex.prod(x[1], x[1])), 0.0,
                               "q1");
         quad[1] = cplex.addLe(cplex.sum(cplex.prod(4.25, x[2], x[2]),
                                         cplex.prod(-2.0, x[2], x[3]),
                                         cplex.prod(4.25, x[3], x[3]),
                                         cplex.prod(-2.0, x[3], x[4]),
                                         cplex.prod(4.0, x[4], x[4]),
                                         cplex.prod(2.0, x[0])), 9.0,
                               "q2");
         quad[2] = cplex.addGe(cplex.diff(cplex.prod(x[5], x[5]),
                                          cplex.prod(x[6], x[6])), 4.0,
                               "q3");

         cplex.addMinimize(cplex.sum(cplex.prod(3.0, x[0]),
                                     cplex.prod(-1.0, x[1]),
                                     cplex.prod(3, x[2]),
                                     cplex.prod(2, x[3]),
                                     cplex.prod(1, x[4]),
                                     cplex.prod(2, x[5]),
                                     cplex.prod(4, x[6])),
                           "obj");

         /* ***************************************************************** *
          *                                                                   *
          *    O P T I M I Z E   P R O B L E M                                *
          *                                                                   *
          * ***************************************************************** */
         cplex.setParam(IloCplex.Param.Barrier.QCPConvergeTol, 1e-10);
         cplex.solve();
         System.out.println("Solution status: " + cplex.getStatus());

         /* ***************************************************************** *
          *                                                                   *
          *    Q U E R Y   S O L U T I O N                                    *
          *                                                                   *
          * ***************************************************************** */
         final double[] xval = cplex.getValues(x);
         final double[] slack = cplex.getSlacks(linear);
         final double[] qslack = cplex.getSlacks(quad);
         final double[] cpi = cplex.getReducedCosts(x);
         final double[] rpi = cplex.getDuals(linear);
         final double[] qpi = getqconstrmultipliers (cplex, xval, ZEROTOL, x, quad);
         // Also store the solution vector in a map since we need to look
         // up solution values by variable and not only by index.
         final HashMap<IloNumVar,Double> xmap = new HashMap<IloNumVar,Double>();
         for (int j = 0; j < x.length; ++j)
            xmap.put(x[j], xval[j]);

         /* ***************************************************************** *
          *                                                                   *
          *    C H E C K   K K T   C O N D I T I O N S                        *
          *                                                                   *
          *    Here we verify that the optimal solution computed by CPLEX     *
          *    (and the qpi[] values computed above) satisfy the KKT          *
          *    conditions.                                                    *
          *                                                                   *
          * ***************************************************************** */
         
         // Primal feasibility: This example is about duals so we skip this test.

         // Dual feasibility: We must verify
         // - for <= constraints (linear or quadratic) the dual
         //   multiplier is non-positive.
         // - for >= constraints (linear or quadratic) the dual
         //   multiplier is non-negative.
         for (int i = 0; i < linear.length; ++i) {
            if ( linear[i].getLB() <= Double.NEGATIVE_INFINITY ) {
               // <= constraint
               if ( rpi[i] > ZEROTOL )
                  throw new RuntimeException("Dual feasibility test failed for row "
                                             + linear[i] + ": " + rpi[i]);
            }
            else if ( linear[i].getUB() >= Double.POSITIVE_INFINITY ) {
               // >= constraint
               if ( rpi[i] < -ZEROTOL )
                  throw new RuntimeException("Dual feasibility test failed for row "
                                             + linear[i] + ": " + rpi[i]);
            }
            else {
               // nothing to do for equality constraints
            }
         }
         for (int i = 0; i < quad.length; ++i) {
            if ( quad[i].getLB() <= Double.NEGATIVE_INFINITY ) {
               // <= constraint
               if ( qpi[i] > ZEROTOL )
                  throw new RuntimeException("Dual feasibility test failed for quad "
                                             + quad[i] + ": " + qpi[i]);
            }
            else if ( quad[i].getUB() >= Double.POSITIVE_INFINITY ) {
               // >= constraint
               if ( qpi[i] < -ZEROTOL )
                  throw new RuntimeException("Dual feasibility test failed for quad "
                                             + quad[i] + ": " + qpi[i]);
            }
            else {
               // nothing to do for equality constraints
            }
         }

         // Complementary slackness.
         // For any constraint the product of primal slack and dual multiplier
         // must be 0.
         for (int i = 0; i < linear.length; ++i) {
            if ( Math.abs(linear[i].getUB() - linear[i].getLB()) > ZEROTOL &&
                 Math.abs(slack[i] * rpi[i]) > ZEROTOL )
               throw new RuntimeException("Complementary slackness test failed for row " + linear[i]
                                          + ": " + Math.abs(slack[i] * rpi[i]));
         }
         for (int i = 0; i < quad.length; ++i) {
            if ( Math.abs(quad[i].getUB() - quad[i].getLB()) > ZEROTOL &&
                 Math.abs(qslack[i] * qpi[i]) > ZEROTOL )
               throw new RuntimeException("Complementary slackness test failed for quad " + quad[i]
                                          + ": " + Math.abs(qslack[i] * qpi[i]));
         }
         for (int j = 0; j < x.length; ++j) {
            if ( x[j].getUB() < Double.POSITIVE_INFINITY ) {
               double slk = x[j].getUB() - xval[j];
               double dual = cpi[j] < -ZEROTOL ? cpi[j] : 0.0;
               if ( Math.abs(slk * dual) > ZEROTOL )
                  throw new RuntimeException("Complementary slackness test failed for column " + x[j]
                                             + ": " + Math.abs(slk * dual));
            }
            if ( x[j].getLB() > Double.NEGATIVE_INFINITY ) {
               double slk = xval[j] - x[j].getLB();
               double dual = cpi[j] > ZEROTOL ? cpi[j] : 0.0;
               if ( Math.abs(slk * dual) > ZEROTOL )
                  throw new RuntimeException("Complementary slackness test failed for column " + x[j]
                                             + ": " + Math.abs(slk * dual));
            }
         }

         // Stationarity.
         // The difference between objective function and gradient at optimal
         // solution multiplied by dual multipliers must be 0, i.e., for the
         // optimal solution x
         // 0 == c
         //      - sum(r in rows)  r'(x)*rpi[r]
         //      - sum(q in quads) q'(x)*qpi[q]
         //      - sum(c in cols)  b'(x)*cpi[c]
         // where r' and q' are the derivatives of a row or quadratic constraint,
         // x is the optimal solution and rpi[r] and qpi[q] are the dual
         // multipliers for row r and quadratic constraint q.
         // b' is the derivative of a bound constraint and cpi[c] the dual bound
         // multiplier for column c.
         final HashMap<IloNumVar,Double> kktsum = new HashMap<IloNumVar,Double>();
         for (int j = 0; j < x.length; ++j)
            kktsum.put(x[j], 0.0);

         // Objective function.
         for (IloLinearNumExprIterator it = ((IloLinearNumExpr)cplex.getObjective().getExpr()).linearIterator();
              it.hasNext(); /* nothing */)
         {
            IloNumVar v = it.nextNumVar();
            kktsum.put(v, it.getValue());
         }

         // Linear constraints.
         // The derivative of a linear constraint ax - b (<)= 0 is just a.
         for (int i = 0; i < linear.length; ++i) {
            for (IloLinearNumExprIterator it = ((IloLinearNumExpr)linear[i].getExpr()).linearIterator();
                 it.hasNext(); /* nothing */)
            {
               IloNumVar v = it.nextNumVar();
               kktsum.put(v, kktsum.get(v) - rpi[i] * it.getValue());
            }
         }

         // Quadratic constraints.
         // The derivative of a constraint xQx + ax - b <= 0 is
         // Qx + Q'x + a.
         for (int i = 0; i < quad.length; ++i) {
            for (IloLinearNumExprIterator it = ((IloLinearNumExpr)quad[i].getExpr()).linearIterator();
                 it.hasNext(); /* nothing */)
            {
               IloNumVar v = it.nextNumVar();
               kktsum.put(v, kktsum.get(v) - qpi[i] * it.getValue());
            }
            for (IloQuadNumExprIterator it = ((IloQuadNumExpr)quad[i].getExpr()).quadIterator();
                 it.hasNext(); /* nothing */)
            {
               it.next();
               IloNumVar v1 = it.getNumVar1();
               IloNumVar v2 = it.getNumVar2();
               kktsum.put(v1, kktsum.get(v1) - qpi[i] * xmap.get(v2) * it.getValue());
               kktsum.put(v2, kktsum.get(v2) - qpi[i] * xmap.get(v1) * it.getValue());
            }
         }

         // Bounds.
         // The derivative for lower bounds is -1 and that for upper bounds
         // is 1.
         // CPLEX already returns dj with the appropriate sign so there is
         // no need to distinguish between different bound types here.
         for (int j = 0; j < x.length; ++j) {
            kktsum.put(x[j], kktsum.get(x[j]) - cpi[j]);
         }

         for (IloNumVar v : x) {
            if ( Math.abs(kktsum.get(v)) > ZEROTOL )
               throw new RuntimeException("Stationarity test failed at " + v
                                          + ": " + Math.abs(kktsum.get(v)));
         }

         // KKT conditions satisfied. Dump out the optimal solutions and
         // the dual values.
         System.out.println("Optimal solution satisfies KKT conditions.");
         System.out.println("   x[] = " + arrayToString(xval));
         System.out.println(" cpi[] = " + arrayToString(cpi));
         System.out.println(" rpi[] = " + arrayToString(rpi));
         System.out.println(" qpi[] = " + arrayToString(qpi));
      }
      catch (IloException e) {
         System.err.println("IloException: " + e.getMessage());
         e.printStackTrace();
         System.exit(-1);
      }
      finally {
         if ( cplex != null )
            cplex.end();
      }
   }
}
