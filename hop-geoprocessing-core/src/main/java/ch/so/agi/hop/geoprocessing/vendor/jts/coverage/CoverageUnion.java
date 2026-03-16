/*
 * Copyright (c) 2022 Martin Davis.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * and Eclipse Distribution License v. 1.0 which accompanies this distribution.
 * The Eclipse Public License is available at http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *
 * http://www.eclipse.org/org/documents/edl-v10.php.
 */
package ch.so.agi.hop.geoprocessing.vendor.jts.coverage;

import org.locationtech.jts.geom.Geometry;

final class CoverageUnion {

  private CoverageUnion() {}

  static Geometry union(Geometry[] coverage) {
    return org.locationtech.jts.coverage.CoverageUnion.union(coverage);
  }
}
