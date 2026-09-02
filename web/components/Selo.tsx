import type { StatusCobranca } from "@/lib/api";
import { rotuloStatus } from "@/lib/formato";

export default function Selo({ estado }: { estado: StatusCobranca }) {
  return (
    <span className="selo" data-estado={estado}>
      {rotuloStatus(estado)}
    </span>
  );
}
