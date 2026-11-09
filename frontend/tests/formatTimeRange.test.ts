import { describe, expect, it } from "vitest";
import { formatTimeRange } from "../src/formatTimeRange";

describe("formatTimeRange", () => {
  it("keeps same-day courses concise", () => {
    expect(formatTimeRange("2034-01-01T10:00", "2034-01-01T11:00")).toBe(
      "2034-01-01 10:00 – 11:00",
    );
  });

  it("shows the end date when a course crosses midnight and the year", () => {
    expect(formatTimeRange("2034-12-31T23:30", "2035-01-01T00:30")).toBe(
      "2034-12-31 23:30 – 2035-01-01 00:30",
    );
  });
});
