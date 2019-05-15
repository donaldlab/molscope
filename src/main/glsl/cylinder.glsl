
float foo() {

	float a1 = n.x*n.x;
	float a2 = n.y*n.y;
	float a3 = n.z*n.z;
	float a4 = n.z*n.z*n.z*n.z; // TODO simplify
	float a5 = a4+(a2+a1-2)*a3+1;
	float a6 = n.x*n.x*n.x; // TODO: simplify
	float a7 = n.z*n.z*n.z; // TODO: simplify
	float a8 = -a1;
	float a9 = a8+2;
	float a10 = n.y*n.y*n.y; // TODO: simplify
	float a11 = -a2;
	float a12 = -a4;
	float a13 = 2*a1;
	float a14 = n.x*n.x*n.x*n.x; // TODO: simplify
	float a15 = a12+(a11-2*a1+2)*a3-a1*a2-a14+a13-1;
	float a16 = -2*n.x*n.y*a3-2*n.x*a10+(4*n.x-2*a6)*n.y;
	float a17 = n.y*n.y*n.y*n.y; // TODO: simplify
	float a18 = a12+(-2*a2+a8+2)*a3-a17+a9*a2-1;
	float a19 = 2*a4;
	float a20 = 2*n.x*n.y*a3+2*n.x*a10+(2*a6-4*n.x)*n.y;

	float z = -(sqrt(a18*ray.y*ray.y+(a16*ray.x+(a19+(4*a2+a13-4)*a3+2*a17+(a13-4)*a2+2)*p.y+a20*p.x)*ray.y+a15*ray.x*ray.x+(a20*p.y+(a19+(2*a2+4*a1-4)*a3+2*a1*a2+2*a14-4*a1+2)*p.x)*ray.x+a5*r*r+a18*p.y*p.y+a16*p.x*p.y+a15*p.x*p.x)+(n.y*a7+(a10+(a1-2)*n.y)*n.z)*ray.y+(n.x*a7+(n.x*a2+a6-2*n.x)*n.z)*ray.x+(a12+(a11+a8+2)*a3-1)*p.z+((a9*n.y-a10)*n.z-n.y*a7)*p.y+((-n.x*a2-a6+2*n.x)*n.z-n.x*a7)*p.x)/(a5);
}
