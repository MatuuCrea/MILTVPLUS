export function hasActiveRights(row, region, now = new Date()) {
  if (!row || row.rights_verified !== true) return false;
  if (typeof row.rights_holder !== "string" || !row.rights_holder.trim()) return false;
  if (typeof row.rights_reference !== "string" || !row.rights_reference.trim()) return false;
  if (!Array.isArray(row.rights_regions) || row.rights_regions.length === 0) return false;

  const regions = row.rights_regions.map((value) => String(value).trim().toUpperCase());
  const requested = String(region ?? "").trim().toUpperCase();
  if (!requested || !(regions.includes(requested) || regions.includes("*") || regions.includes("001"))) return false;

  const start = Date.parse(row.rights_valid_from ?? "");
  if (!Number.isFinite(start) || start > now.getTime()) return false;
  if (row.rights_valid_until != null && row.rights_valid_until !== "") {
    const end = Date.parse(row.rights_valid_until);
    if (!Number.isFinite(end) || end <= now.getTime()) return false;
  }
  return true;
}

export function isSafeHttpsUrl(value) {
  if (typeof value !== "string" || value.length > 4096) return false;
  try {
    const url = new URL(value);
    return url.protocol === "https:" && Boolean(url.hostname) &&
      !url.username && !url.password && !url.port.match(/^0+$/);
  } catch {
    return false;
  }
}
